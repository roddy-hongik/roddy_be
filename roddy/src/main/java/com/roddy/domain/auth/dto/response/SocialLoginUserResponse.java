package com.roddy.domain.auth.dto.response;

public record SocialLoginUserResponse(
        String id,
        String email,
        String nickname
) {
}
