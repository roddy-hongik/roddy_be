package com.roddy.domain.onboarding.dto.response;

public record GithubConnectionStatusResponse(
        boolean isOnboard,
        boolean githubConnected,
        String githubId,
        String githubUrl
) {
}
