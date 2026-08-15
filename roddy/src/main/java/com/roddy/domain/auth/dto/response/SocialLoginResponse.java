package com.roddy.domain.auth.dto.response;

import com.roddy.domain.auth.entity.User;

public record SocialLoginResponse(
        String accessToken,
        String refreshToken,
        boolean isOnboard,
        boolean githubConnected,
        SocialLoginUserResponse user
) {

    public static SocialLoginResponse from(LoginResponse loginResponse, User user) {
        return new SocialLoginResponse(
                loginResponse.accessToken(),
                loginResponse.refreshToken(),
                loginResponse.isOnboard(),
                loginResponse.githubConnected(),
                new SocialLoginUserResponse(
                        String.valueOf(user.getId()),
                        user.getEmail(),
                        user.getNickname()
                )
        );
    }
}
