package com.roddy.domain.jobposting.controller;

import com.roddy.domain.jobposting.dto.response.CrawlingDashboardResponse;
import com.roddy.domain.jobposting.service.AdminCrawlingService;
import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.domain.jobposting.service.JobRoleBackfillService;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/admin/crawling")
@RequiredArgsConstructor
@Tag(name = "AdminCrawling", description = "어드민 채용공고 수집 현황 API")
public class AdminCrawlingController {

    private final AdminCrawlingService adminCrawlingService;

    private final JobRoleBackfillService jobRoleBackfillService;

    @PostMapping("/classify-jobs")
    @Operation(summary = "기존 미분류 공고 직무 보정", description = "200건씩 처리한다. afterId=0부터 시작해 nextAfterId로 반복한다. 기존 분류와 알림은 변경하지 않는다.")
    public ApiResponse<JobRoleBackfillService.Result> classifyJobs(
            @RequestParam(defaultValue = "0") @Min(0) long afterId) {
        return ApiResponse.onSuccess("공고 직무를 보정했습니다.", jobRoleBackfillService.backfill(afterId));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "수집 현황 조회", description = "명세에 있는 회사 전체의 오늘 수집 결과와 마지막 상태")
    public ApiResponse<CrawlingDashboardResponse> getDashboard() {
        return ApiResponse.onSuccess("수집 현황을 조회했습니다.", adminCrawlingService.getDashboard());
    }

    @PostMapping("/run")
    @Operation(
            summary = "수집 바로 실행",
            description = "명세에 있는 회사 전체를 지금 수집한다. 수 분이 걸리므로 바로 현황을 돌려주고, "
                    + "running 이 꺼질 때까지 현황을 다시 조회하면 된다. 이미 수집이 돌고 있으면 409"
    )
    public ApiResponse<CrawlingDashboardResponse> startCrawling() {
        return ApiResponse.onSuccess("채용공고 수집을 시작했습니다.", adminCrawlingService.startCrawling());
    }
}
