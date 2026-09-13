package com.roddy.domain.auth.dto.response;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.enums.Role;

public record SocialLoginResponse(
        String accessToken,
        String refreshToken,
        boolean isOnboard,
        boolean githubConnected,
        Role role,
        SocialLoginUserResponse user
) {

    public static SocialLoginResponse from(LoginResponse loginResponse, User user) {
        return new SocialLoginResponse(
                loginResponse.accessToken(),
                loginResponse.refreshToken(),
                loginResponse.isOnboard(),
                loginResponse.githubConnected(),
                loginResponse.role(),
                new SocialLoginUserResponse(
                        String.valueOf(user.getId()),
                        user.getEmail(),
                        user.getNickname()
                )
        );
    }
}
