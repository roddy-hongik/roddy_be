package com.roddy.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SocialLoginRequest(
        @NotBlank String accessToken
) {
}
