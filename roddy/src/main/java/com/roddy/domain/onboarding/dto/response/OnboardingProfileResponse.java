package com.roddy.domain.onboarding.dto.response;

public record OnboardingProfileResponse(
        String name,
        int age,
        String experienceLevel,
        String desiredJob,
        String desiredCompany,
        String portfolioFileName,
        boolean isOnboard,
        boolean githubConnected
) {
}
