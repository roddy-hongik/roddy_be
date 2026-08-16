package com.roddy.domain.mypage.service;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.mypage.entity.DesiredCompany;
import com.roddy.domain.mypage.dto.request.MyPageProfileUpdateRequest;
import com.roddy.domain.mypage.dto.response.MyPageProfileResponse;
import com.roddy.domain.mypage.repository.DesiredCompanyRepository;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyPageService {

    private static final String REFRESH_TOKEN_PREFIX = "RefreshToken:";

    private final UserRepository userRepository;
    private final DesiredCompanyRepository desiredCompanyRepository;
    private final StringRedisTemplate redisTemplate;

    @Transactional(readOnly = true)
    public MyPageProfileResponse getProfile(Long userId) {
        User user = getActiveUser(userId);
        return toProfileResponse(user);
    }

    @Transactional
    public MyPageProfileResponse updateProfile(Long userId, MyPageProfileUpdateRequest request) {
        User user = getActiveUser(userId);
        user.updateMyPageProfile(
                request.getName().trim(),
                request.getAge(),
                request.getProfileImageUrl()
        );
        return toProfileResponse(user);
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = getActiveUser(userId);
        user.withdraw();
        redisTemplate.delete(getRefreshTokenKey(userId));
    }

    private MyPageProfileResponse toProfileResponse(User user) {
        String desiredCompany = desiredCompanyRepository.findByUserId(user.getId())
                .map(DesiredCompany::getDesiredCompany)
                .orElse(null);

        return new MyPageProfileResponse(
                user.getNickname(),
                user.getAge(),
                user.getProfileImageUrl(),
                user.getDesiredJob() == null ? null : user.getDesiredJob().name(),
                desiredCompany,
                user.getExperienceYears() == null ? null : user.getExperienceYears().name(),
                user.getPortfolioFileName(),
                user.getPortfolioUrl(),
                user.isGithubConnected()
        );
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
