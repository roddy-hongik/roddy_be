package com.roddy.domain.analysis.controller;

import com.roddy.domain.analysis.dto.response.AnalysisReportResponse;
import com.roddy.domain.analysis.service.AnalysisService;
import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Tag(name = "Analysis", description = "역량 분석 API")
public class AnalysisController {

    private final AnalysisService analysisService;

    @PostMapping("/me")
    @Operation(
            summary = "역량 분석 요청",
            description = "분석은 수십 초가 걸린다. 바로 PENDING 을 돌려주고, 끝나면 조회로 결과를 본다."
    )
    public ApiResponse<AnalysisReportResponse> requestAnalysis(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "역량 분석을 시작했습니다.",
                analysisService.requestAnalysis(userDetails.getUser().getId())
        );
    }

    @GetMapping("/me")
    @Operation(summary = "내 역량 분석 리포트 조회")
    public ApiResponse<AnalysisReportResponse> getMyReport(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "역량 분석 리포트를 조회했습니다.",
                analysisService.getReport(userDetails.getUser().getId())
        );
    }
}
