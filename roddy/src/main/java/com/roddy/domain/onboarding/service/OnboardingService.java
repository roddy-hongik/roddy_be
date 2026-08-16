package com.roddy.domain.onboarding.service;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.enums.ExperienceLevel;
import com.roddy.domain.mypage.entity.DesiredCompany;
import com.roddy.domain.mypage.repository.DesiredCompanyRepository;
import com.roddy.domain.onboarding.dto.request.OnboardingProfileRequest;
import com.roddy.domain.onboarding.dto.request.PortfolioPresignRequest;
import com.roddy.domain.onboarding.dto.response.OnboardingProfileResponse;
import com.roddy.domain.onboarding.dto.response.PortfolioPresignResponse;
import com.roddy.global.config.s3.S3ObjectUrlService;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private static final String PORTFOLIO_CONTENT_TYPE = "application/pdf";

    private final UserRepository userRepository;
    private final DesiredCompanyRepository desiredCompanyRepository;
    private final S3ObjectUrlService s3ObjectUrlService;
    private final S3Client s3Client;

    @Value("${app.portfolio.presign-expiration-minutes:10}")
    private long portfolioPresignExpirationMinutes;

    public PortfolioPresignResponse createPortfolioPresignedUrl(Long userId, PortfolioPresignRequest request) {
        validatePdfFileName(request.fileName());

        String objectKey = buildPortfolioObjectKey(userId);
        String uploadUrl = s3ObjectUrlService.createPresignedPutUrl(
                objectKey,
                PORTFOLIO_CONTENT_TYPE,
                portfolioPresignExpirationMinutes
        );

        return new PortfolioPresignResponse(
                uploadUrl,
                objectKey,
                request.fileName().trim(),
                PORTFOLIO_CONTENT_TYPE,
                portfolioPresignExpirationMinutes
        );
    }

    @Transactional
    public OnboardingProfileResponse completeOnboarding(Long userId, OnboardingProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

        validatePdfFileName(request.getPortfolioFileName());
        validatePortfolioObjectOwnership(userId, request.getPortfolioObjectKey());
        ensurePortfolioExists(request.getPortfolioObjectKey());

        ExperienceLevel experienceLevel = toExperienceLevel(request.getExperienceYears());
        DesiredJob desiredJob = request.getDesiredJob();

        user.completeProfile(
                request.getName().trim(),
                request.getAge(),
                experienceLevel,
                desiredJob,
                s3ObjectUrlService.createPresignedGetUrl(request.getPortfolioObjectKey()),
                request.getPortfolioFileName().trim(),
                LocalDateTime.now()
        );

        desiredCompanyRepository.findByUserId(userId)
                .ifPresentOrElse(
                        desiredCompany -> desiredCompany.update(desiredJob, request.getDesiredCompany().trim()),
                        () -> desiredCompanyRepository.save(
                                DesiredCompany.create(user, desiredJob, request.getDesiredCompany().trim())
                        )
                );

        return new OnboardingProfileResponse(
                user.getNickname(),
                user.getAge(),
                user.getExperienceYears().name(),
                user.getDesiredJob().name(),
                request.getDesiredCompany().trim(),
                user.getPortfolioFileName(),
                user.isOnboarded(),
                user.isGithubConnected()
        );
    }

    private void validatePdfFileName(String fileName) {
        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "포트폴리오는 PDF 파일만 업로드할 수 있습니다.");
        }
    }

    private void validatePortfolioObjectOwnership(Long userId, String objectKey) {
        String expectedPrefix = "portfolio/" + userId + "/";
        if (objectKey == null || !objectKey.startsWith(expectedPrefix)) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "포트폴리오 파일 키가 올바르지 않습니다.");
        }
    }

    private void ensurePortfolioExists(String objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(s3ObjectUrlService.getBucket())
                    .key(objectKey)
                    .build());
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "업로드된 포트폴리오 파일을 찾을 수 없습니다.");
            }
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR, "포트폴리오 파일 확인에 실패했습니다.");
        } catch (GeneralException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR, "포트폴리오 파일 확인에 실패했습니다.");
        }
    }

    private ExperienceLevel toExperienceLevel(int experienceYears) {
        if (experienceYears < 0) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "경력 연수는 0 이상이어야 합니다.");
        }
        if (experienceYears == 0) {
            return ExperienceLevel.NEWBIE;
        }
        if (experienceYears <= 3) {
            return ExperienceLevel.JUNIOR;
        }
        if (experienceYears <= 7) {
            return ExperienceLevel.MIDDLE;
        }
        return ExperienceLevel.SENIOR;
    }

    private String buildPortfolioObjectKey(Long userId) {
        return "portfolio/" + userId + "/" + UUID.randomUUID() + ".pdf";
    }
}
