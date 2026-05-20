package com.roddy.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ReissueTokenRequest(
        @NotBlank String accessToken,
        @NotBlank String refreshToken
) {
}
