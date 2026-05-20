package com.roddy.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
        @NotBlank String accessToken,
        @NotBlank String refreshToken
) {
}
