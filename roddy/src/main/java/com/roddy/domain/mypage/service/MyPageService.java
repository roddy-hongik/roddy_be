package com.roddy.domain.mypage.service;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.mypage.entity.DesiredCompany;
import com.roddy.domain.mypage.dto.request.MyPageProfileUpdateRequest;
import com.roddy.domain.mypage.dto.request.ProfileImagePresignRequest;
import com.roddy.domain.mypage.dto.response.MyPageProfileResponse;
import com.roddy.domain.mypage.dto.response.ProfileImagePresignResponse;
import com.roddy.domain.mypage.repository.DesiredCompanyRepository;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import com.roddy.global.config.s3.S3ObjectUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MyPageService {

    private static final String REFRESH_TOKEN_PREFIX = "RefreshToken:";
    private static final String PROFILE_IMAGE_KEY_PREFIX = "profile-image/";
    /** 프로필 이미지는 자주 불러온다. 크게 올리면 볼 때마다 느려지므로 크기를 묶는다. */
    private static final long PROFILE_IMAGE_MAX_BYTES = 5L * 1024 * 1024;
    private static final Map<String, String> PROFILE_IMAGE_CONTENT_TYPES = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg"
    );

    private final UserRepository userRepository;
    private final DesiredCompanyRepository desiredCompanyRepository;
    private final StringRedisTemplate redisTemplate;
    private final S3ObjectUrlService s3ObjectUrlService;
    private final S3Client s3Client;

    @Value("${app.profile-image.presign-expiration-minutes:10}")
    private long profileImagePresignExpirationMinutes;

    @Transactional(readOnly = true)
    public MyPageProfileResponse getProfile(Long userId) {
        User user = getActiveUser(userId);
        return toProfileResponse(user);
    }

    @Transactional
    public MyPageProfileResponse updateProfile(Long userId, MyPageProfileUpdateRequest request) {
        User user = getActiveUser(userId);
        user.updateMyPageProfile(request.getName().trim(), request.getAge());
        applyProfileImage(user, request);
        return toProfileResponse(user);
    }

    /**
     * 프로필 이미지를 S3 에 직접 올릴 presigned PUT 주소를 만든다.
     *
     * <p>파일은 서버를 거치지 않는다. 올린 뒤 받은 키를 프로필 수정에 보내야 프로필에 걸린다.
     */
    @Transactional(readOnly = true)
    public ProfileImagePresignResponse createProfileImagePresignedUrl(Long userId, ProfileImagePresignRequest request) {
        getActiveUser(userId);

        String extension = extensionOf(request.fileName());
        String contentType = PROFILE_IMAGE_CONTENT_TYPES.get(extension);
        if (contentType == null) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "프로필 이미지는 png, jpg 파일만 올릴 수 있습니다.");
        }

        String objectKey = PROFILE_IMAGE_KEY_PREFIX + userId + "/" + UUID.randomUUID() + "." + extension;
        String uploadUrl = s3ObjectUrlService.createPresignedPutUrl(
                objectKey,
                contentType,
                profileImagePresignExpirationMinutes
        );

        return new ProfileImagePresignResponse(uploadUrl, objectKey, contentType, profileImagePresignExpirationMinutes);
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = getActiveUser(userId);
        user.withdraw();
        redisTemplate.delete(getRefreshTokenKey(userId));
    }

    /** 새 키가 오면 바꾸고, 지우라고 하면 지우고, 둘 다 아니면 지금 이미지를 그대로 둔다. */
    private void applyProfileImage(User user, MyPageProfileUpdateRequest request) {
        String objectKey = request.getProfileImageObjectKey();
        boolean hasNewImage = objectKey != null && !objectKey.isBlank();

        if (request.isRemovingProfileImage() && hasNewImage) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "프로필 이미지를 바꾸면서 지울 수는 없습니다.");
        }
        if (request.isRemovingProfileImage()) {
            user.removeProfileImage();
            return;
        }
        if (hasNewImage) {
            String trimmedKey = objectKey.trim();
            validateProfileImageOwnership(user.getId(), trimmedKey);
            ensureProfileImageUploaded(trimmedKey);
            user.changeProfileImage(trimmedKey);
        }
    }

    /** 남의 이미지나 포트폴리오 같은 다른 파일을 프로필로 걸 수 없게, presign 이 만든 내 경로인지 본다. */
    private void validateProfileImageOwnership(Long userId, String objectKey) {
        if (!objectKey.startsWith(PROFILE_IMAGE_KEY_PREFIX + userId + "/")) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "프로필 이미지 키가 올바르지 않습니다.");
        }
    }

    /**
     * 실제로 올라갔는지, 너무 크지 않은지 본다.
     *
     * <p>presigned PUT 은 올리는 크기를 막지 못해서 올라온 뒤에 확인한다.
     */
    private void ensureProfileImageUploaded(String objectKey) {
        HeadObjectResponse uploaded;
        try {
            uploaded = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3ObjectUrlService.getBucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "업로드된 프로필 이미지를 찾을 수 없습니다.");
            }
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR, "프로필 이미지 확인에 실패했습니다.");
        } catch (RuntimeException exception) {
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR, "프로필 이미지 확인에 실패했습니다.");
        }

        if (uploaded.contentLength() != null && uploaded.contentLength() > PROFILE_IMAGE_MAX_BYTES) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "프로필 이미지는 5MB 까지 올릴 수 있습니다.");
        }
    }

    private MyPageProfileResponse toProfileResponse(User user) {
        String desiredCompany = desiredCompanyRepository.findByUserId(user.getId())
                .map(DesiredCompany::getDesiredCompany)
                .orElse(null);

        return new MyPageProfileResponse(
                user.getNickname(),
                user.getAge(),
                presignedGetUrl(user.getProfileImageObjectKey()),
                user.getDesiredJob() == null ? null : user.getDesiredJob().name(),
                desiredCompany,
                user.getExperienceYears() == null ? null : user.getExperienceYears().name(),
                user.getPortfolioFileName(),
                presignedGetUrl(user.getPortfolioObjectKey()),
                user.isGithubConnected()
        );
    }

    /** 저장해 둔 키로 볼 때마다 새 주소를 만든다. presigned 주소는 몇 분이면 만료된다. */
    private String presignedGetUrl(String objectKey) {
        return (objectKey == null || objectKey.isBlank())
                ? null
                : s3ObjectUrlService.createPresignedGetUrl(objectKey);
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).trim().toLowerCase(Locale.ROOT);
    }

    private User getActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        if (user.isWithdrawn()) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN, "탈퇴한 사용자입니다.");
        }
        return user;
    }

    private String getRefreshTokenKey(Long userId) {
        return REFRESH_TOKEN_PREFIX + userId;
    }
}
