package com.roddy.domain.interview.controller;

import com.roddy.domain.interview.dto.InterviewQuestionsResponse;
import com.roddy.domain.interview.dto.InterviewSessionListResponse;
import com.roddy.domain.interview.dto.InterviewSessionResponse;
import com.roddy.domain.interview.dto.SubmitInterviewAnswersRequest;
import com.roddy.domain.interview.service.InterviewService;
import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/mock-interview")
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping("/questions")
    @Operation(summary = "AI 모의면접 질문 생성")
    public ApiResponse<InterviewQuestionsResponse> generateQuestions(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ApiResponse.onSuccess(
                "모의면접 질문을 생성했습니다.",
                interviewService.generateQuestions(userDetails.getUser().getId()));
    }

    @PostMapping("/sessions")
    @Operation(summary = "모의면접 답변 제출 및 AI 채점", description = "생성된 질문 3개에 대한 답변을 함께 보내면 AI가 채점하고, 결과를 한 회차로 저장합니다.")
    public ApiResponse<InterviewSessionResponse> submitAnswers(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody SubmitInterviewAnswersRequest request) {
        return ApiResponse.onSuccess(
                "모의면접 답변을 채점했습니다.",
                interviewService.submitAnswers(userDetails.getUser().getId(), request));
    }

    @GetMapping("/sessions")
    @Operation(summary = "내 모의면접 회차 목록", description = "최신순. page는 0부터 시작합니다.")
    public ApiResponse<InterviewSessionListResponse> getSessions(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.onSuccess(
                "모의면접 회차 목록을 조회했습니다.",
                interviewService.getSessions(userDetails.getUser().getId(), page, size));
    }

    @GetMapping("/sessions/{id}")
    @Operation(summary = "모의면접 회차 상세 조회", description = "해당 회차의 질문·답변·AI 피드백을 모두 보여줍니다.")
    public ApiResponse<InterviewSessionResponse> getSession(
            @AuthenticationPrincipal UserDetailsImpl userDetails, @PathVariable Long id) {
        return ApiResponse.onSuccess(
                "모의면접 회차를 조회했습니다.",
                interviewService.getSession(userDetails.getUser().getId(), id));
    }
}
