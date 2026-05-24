package com.roddy.domain.onboarding.controller;

import com.roddy.domain.onboarding.dto.request.OnboardingProfileRequest;
import com.roddy.domain.onboarding.dto.response.OnboardingProfileResponse;
import com.roddy.domain.onboarding.service.OnboardingService;
import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
@Tag(name = "Onboarding", description = "소셜 로그인 이후 온보딩 정보 입력 API")
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "온보딩 정보 저장", description = "이름, 나이, 경력, 희망 직종, 희망 기업, 포트폴리오 PDF를 저장하고 온보딩을 완료합니다.")
    public ApiResponse<OnboardingProfileResponse> completeOnboarding(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @ModelAttribute OnboardingProfileRequest request
    ) {
        OnboardingProfileResponse response =
                onboardingService.completeOnboarding(userDetails.getUser().getId(), request);
        return ApiResponse.onSuccess("온보딩 정보가 저장되었습니다.", response);
    }
}
