package com.roddy.domain.onboarding.dto.response;

public record PortfolioPresignResponse(
        String uploadUrl,
        String objectKey,
        String fileName,
        String contentType,
        long expiresInMinutes
) {
}
