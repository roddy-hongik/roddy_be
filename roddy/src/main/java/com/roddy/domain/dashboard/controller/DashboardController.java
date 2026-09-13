package com.roddy.domain.dashboard.controller;

import com.roddy.domain.dashboard.dto.response.DashboardSummaryResponse;
import com.roddy.domain.dashboard.service.DashboardService;
import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "메인 대시보드 API")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(
            summary = "메인 대시보드 요약",
            description = "가장 최근에 끝난 역량 분석 리포트와 매칭률이 높은 모집 중 공고를 모아 준다. "
                    + "끝난 분석이 없으면 점수와 추천은 비어 있다."
    )
    public ApiResponse<DashboardSummaryResponse> getSummary(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "대시보드를 조회했습니다.",
                dashboardService.getSummary(userDetails.getUser().getId())
        );
    }
}
