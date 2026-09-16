package com.roddy.domain.analysis.controller;

import com.roddy.domain.analysis.dto.response.AnalysisReportListResponse;
import com.roddy.domain.analysis.dto.response.AnalysisReportResponse;
import com.roddy.domain.analysis.service.AnalysisService;
import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
            description = "분석은 수십 초가 걸린다. 바로 PENDING 을 돌려주고, 끝나면 조회로 결과를 본다. "
                    + "리포트는 요청할 때마다 새로 쌓인다."
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
    @Operation(
            summary = "가장 최근 분석 조회",
            description = "가장 최근에 요청한 분석. 진행 중이거나 실패한 것일 수 있다. 분석을 요청한 뒤 끝났는지 볼 때 쓴다."
    )
    public ApiResponse<AnalysisReportResponse> getMyReport(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "역량 분석 리포트를 조회했습니다.",
                analysisService.getReport(userDetails.getUser().getId())
        );
    }

    @GetMapping("/reports/me")
    @Operation(
            summary = "내 리포트 목록",
            description = "끝난 리포트만 최신순으로 준다. 본문과 축별 해석, 기술스택은 싣지 않는다."
    )
    public ApiResponse<AnalysisReportListResponse> getMyReports(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "내 리포트 목록을 조회했습니다.",
                analysisService.getMyReports(userDetails.getUser().getId())
        );
    }

    @GetMapping("/reports/{reportId}")
    @Operation(
            summary = "리포트 상세",
            description = "내 리포트 한 건. 남의 리포트는 없는 리포트와 똑같이 404 로 답한다."
    )
    public ApiResponse<AnalysisReportResponse> getReport(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long reportId
    ) {
        return ApiResponse.onSuccess(
                "역량 분석 리포트를 조회했습니다.",
                analysisService.getMyReport(userDetails.getUser().getId(), reportId)
        );
    }
}
