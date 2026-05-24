package com.roddy.domain.onboarding.controller;

import com.roddy.domain.onboarding.dto.request.PortfolioPresignRequest;
import com.roddy.domain.onboarding.dto.request.OnboardingProfileRequest;
import com.roddy.domain.onboarding.dto.response.OnboardingProfileResponse;
import com.roddy.domain.onboarding.dto.response.PortfolioPresignResponse;
import com.roddy.domain.onboarding.service.OnboardingService;
import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
@Tag(name = "Onboarding", description = "소셜 로그인 이후 온보딩 정보 입력 API")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/portfolio/presign")
    @Operation(summary = "포트폴리오 업로드 presigned URL 발급", description = "PDF 포트폴리오를 S3에 직접 업로드할 수 있는 presigned PUT URL을 발급합니다.")
    public ApiResponse<PortfolioPresignResponse> createPortfolioPresignedUrl(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody PortfolioPresignRequest request
    ) {
        PortfolioPresignResponse response =
                onboardingService.createPortfolioPresignedUrl(userDetails.getUser().getId(), request);
        return ApiResponse.onSuccess("포트폴리오 업로드 URL이 발급되었습니다.", response);
    }

    @PostMapping
    @Operation(summary = "온보딩 정보 저장", description = "이름, 나이, 경력, 희망 직종, 희망 기업, 포트폴리오 PDF를 저장하고 온보딩을 완료합니다.")
    public ApiResponse<OnboardingProfileResponse> completeOnboarding(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody OnboardingProfileRequest request
    ) {
        OnboardingProfileResponse response =
                onboardingService.completeOnboarding(userDetails.getUser().getId(), request);
        return ApiResponse.onSuccess("온보딩 정보가 저장되었습니다.", response);
    }
}
