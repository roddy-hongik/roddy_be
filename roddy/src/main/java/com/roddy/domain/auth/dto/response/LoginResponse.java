package com.roddy.domain.auth.dto.response;

import lombok.Builder;

@Builder
public record LoginResponse(
        String accessToken,
        String refreshToken,
        boolean isOnboard,
        boolean githubConnected
) {
}
