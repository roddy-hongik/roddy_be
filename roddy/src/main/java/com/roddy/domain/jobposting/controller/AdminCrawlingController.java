package com.roddy.domain.jobposting.controller;

import com.roddy.domain.jobposting.dto.response.CrawlingDashboardResponse;
import com.roddy.domain.jobposting.service.AdminCrawlingService;
import com.roddy.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/crawling")
@RequiredArgsConstructor
@Tag(name = "AdminCrawling", description = "어드민 채용공고 수집 현황 API")
public class AdminCrawlingController {

    private final AdminCrawlingService adminCrawlingService;

    @GetMapping("/dashboard")
    @Operation(summary = "수집 현황 조회", description = "명세에 있는 회사 전체의 오늘 수집 결과와 마지막 상태")
    public ApiResponse<CrawlingDashboardResponse> getDashboard() {
        return ApiResponse.onSuccess("수집 현황을 조회했습니다.", adminCrawlingService.getDashboard());
    }
}
