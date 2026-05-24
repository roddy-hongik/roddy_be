package com.roddy.domain.onboarding.controller;

import com.roddy.domain.onboarding.dto.response.GithubConnectionStatusResponse;
import com.roddy.domain.onboarding.dto.response.GithubOAuthStartResponse;
import com.roddy.domain.onboarding.service.GithubOnboardingService;
import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@RequestMapping("/api/onboarding/github")
@RequiredArgsConstructor
@Tag(name = "Onboarding Github", description = "온보딩 단계의 GitHub 연동 API")
public class OnboardingGithubController {

    private final GithubOnboardingService githubOnboardingService;

    @GetMapping
    @Operation(summary = "GitHub 연동 상태 조회", description = "현재 로그인한 사용자의 GitHub 연동 상태를 조회합니다.")
    public ApiResponse<GithubConnectionStatusResponse> getGithubConnectionStatus(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        GithubConnectionStatusResponse response =
                githubOnboardingService.getConnectionStatus(userDetails.getUser().getId());
        return ApiResponse.onSuccess("GitHub 연동 상태를 조회했습니다.", response);
    }

    @GetMapping("/authorize")
    @Operation(summary = "GitHub 인증 URL 발급", description = "GitHub OAuth 동의 화면으로 이동할 URL을 발급합니다.")
    public ApiResponse<GithubOAuthStartResponse> authorizeGithub(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        GithubOAuthStartResponse response =
                githubOnboardingService.createAuthorizationUrl(userDetails.getUser().getId());
        return ApiResponse.onSuccess("GitHub 인증 URL을 생성했습니다.", response);
    }

    @GetMapping("/callback")
    @Operation(summary = "GitHub OAuth 콜백", description = "GitHub 인가 코드를 교환해 현재 사용자 계정과 연동합니다.")
    public RedirectView githubCallback(
            @RequestParam String code,
            @RequestParam String state
    ) {
        return new RedirectView(githubOnboardingService.connectGithub(code, state));
    }
}
