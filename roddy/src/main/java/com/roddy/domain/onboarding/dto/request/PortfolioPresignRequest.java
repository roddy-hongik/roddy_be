package com.roddy.domain.onboarding.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PortfolioPresignRequest(
        @NotBlank
        String fileName
) {
}
