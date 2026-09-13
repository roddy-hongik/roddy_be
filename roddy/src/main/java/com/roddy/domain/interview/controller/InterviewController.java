package com.roddy.domain.interview.controller;

import com.roddy.domain.interview.dto.InterviewQuestionsResponse;
import com.roddy.domain.interview.service.InterviewService;
import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
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
}
