package com.roddy.domain.coverletter.controller;

import com.roddy.domain.coverletter.dto.*;
import com.roddy.domain.coverletter.service.CoverLetterService;
import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/cover-letters")
@Tag(name = "CoverLetter", description = "내 자기소개서와 문항별 답변 관리")
public class CoverLetterController {
    private final CoverLetterService service;

    @GetMapping
    @Operation(summary = "내 자기소개서 목록", description = "수정일 최신순. page는 0부터 시작합니다.")
    public ApiResponse<CoverLetterListResponse> list(@AuthenticationPrincipal UserDetailsImpl principal,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.onSuccess("자기소개서 목록을 조회했습니다.", service.list(principal.getUser().getId(), page, size));
    }

    @PostMapping
    @Operation(summary = "자기소개서 작성", description = "공고 연결은 선택 사항입니다. 빈 답변도 저장할 수 있습니다.")
    public ApiResponse<CoverLetterResponse> create(@AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody SaveCoverLetterRequest request) {
        return ApiResponse.onSuccess("자기소개서를 저장했습니다.", service.create(principal.getUser().getId(), request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "내 자기소개서 상세 조회")
    public ApiResponse<CoverLetterResponse> get(@AuthenticationPrincipal UserDetailsImpl principal, @PathVariable Long id) {
        return ApiResponse.onSuccess("자기소개서를 조회했습니다.", service.get(principal.getUser().getId(), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "자기소개서 전체 수정", description = "조회한 version과 문항 배열 전체를 보냅니다. 배열 순서가 문항 순서입니다. 오래된 버전은 409로 응답합니다.")
    public ApiResponse<CoverLetterResponse> update(@AuthenticationPrincipal UserDetailsImpl principal, @PathVariable Long id,
            @Valid @RequestBody SaveCoverLetterRequest request) {
        return ApiResponse.onSuccess("자기소개서를 수정했습니다.", service.update(principal.getUser().getId(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "자기소개서 삭제", description = "현재 문서의 version이 필요합니다. 문항도 함께 삭제됩니다.")
    public ApiResponse<Void> delete(@AuthenticationPrincipal UserDetailsImpl principal, @PathVariable Long id,
            @RequestParam @Min(0) long version) {
        service.delete(principal.getUser().getId(), id, version);
        return ApiResponse.onSuccess("자기소개서를 삭제했습니다.");
    }
}
