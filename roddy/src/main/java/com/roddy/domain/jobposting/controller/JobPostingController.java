package com.roddy.domain.jobposting.controller;

import com.roddy.domain.jobposting.dto.request.JobPostingSearchCondition;
import com.roddy.domain.jobposting.dto.response.JobPostingDetailResponse;
import com.roddy.domain.jobposting.dto.response.JobPostingListItemResponse;
import com.roddy.domain.jobposting.dto.response.JobPostingListResponse;
import com.roddy.domain.jobposting.dto.response.JobPostingMatchResponse;
import com.roddy.domain.jobposting.dto.response.ToggleJobScrapResponse;
import com.roddy.domain.jobposting.service.JobPostingMatchService;
import com.roddy.domain.jobposting.service.JobPostingService;
import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Tag(name = "JobPosting", description = "채용공고 API")
public class JobPostingController {

    private final JobPostingService jobPostingService;
    private final JobPostingMatchService jobPostingMatchService;

    @GetMapping
    @Operation(summary = "채용공고 목록 조회", description = "sort=match 면 매칭률 높은 순. 그 외에는 게시일 최신순")
    public ApiResponse<JobPostingListResponse> getJobPostings(
            @Valid @ModelAttribute JobPostingSearchCondition condition,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String sort,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "채용공고 목록을 조회했습니다.",
                jobPostingService.getJobPostings(condition, page, size, sort, userIdOf(userDetails))
        );
    }

    @GetMapping("/scraps/me")
    @Operation(summary = "내가 스크랩한 채용공고 목록 조회")
    public ApiResponse<List<JobPostingListItemResponse>> getMyScraps(
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "스크랩한 채용공고를 조회했습니다.",
                jobPostingService.getMyScrappedJobPostings(userDetails.getUser().getId())
        );
    }

    @GetMapping("/{jobPostingId}")
    @Operation(summary = "채용공고 상세 조회")
    public ApiResponse<JobPostingDetailResponse> getJobPosting(
            @PathVariable Long jobPostingId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "채용공고 상세를 조회했습니다.",
                jobPostingService.getJobPosting(jobPostingId, userIdOf(userDetails))
        );
    }

    @GetMapping("/{jobPostingId}/match")
    @Operation(summary = "채용공고 매칭률 조회", description = "공고가 요구하는 기술과 내 기술스택의 적합도")
    public ApiResponse<JobPostingMatchResponse> getMatch(
            @PathVariable Long jobPostingId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "채용공고 매칭률을 조회했습니다.",
                jobPostingMatchService.getMatch(jobPostingId, userDetails.getUser().getId())
        );
    }

    @PostMapping("/{jobPostingId}/scrap")
    @Operation(summary = "채용공고 스크랩 토글")
    public ApiResponse<ToggleJobScrapResponse> toggleScrap(
            @PathVariable Long jobPostingId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "채용공고 스크랩 상태를 변경했습니다.",
                jobPostingService.toggleScrap(jobPostingId, userDetails.getUser().getId())
        );
    }

    /** 목록과 상세는 로그인하지 않아도 볼 수 있다. 이때는 스크랩 여부만 비워 둔다. */
    private Long userIdOf(UserDetailsImpl userDetails) {
        return userDetails == null ? null : userDetails.getUser().getId();
    }
}
