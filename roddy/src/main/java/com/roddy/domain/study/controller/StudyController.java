package com.roddy.domain.study.controller;

import com.roddy.domain.study.dto.request.StudyPostCreateRequest;
import com.roddy.domain.study.dto.request.StudySearchCondition;
import com.roddy.domain.study.dto.request.StudyApplicationStatusUpdateRequest;
import com.roddy.domain.study.dto.response.MyStudyApplicationListResponse;
import com.roddy.domain.study.dto.response.StudyApplicationResponse;
import com.roddy.domain.study.dto.response.StudyCloseResponse;
import com.roddy.domain.study.dto.response.StudyPostCreateResponse;
import com.roddy.domain.study.dto.response.StudyPostDetailResponse;
import com.roddy.domain.study.dto.response.StudyPostListResponse;
import com.roddy.domain.study.enums.StudyApplicationStatus;
import com.roddy.domain.study.service.StudyService;
import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import com.roddy.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/studies")
@RequiredArgsConstructor
@Tag(name = "Study", description = "스터디 모집 API")
public class StudyController {

    private final StudyService studyService;

    @GetMapping
    @Operation(summary = "스터디 모집글 목록 조회")
    public ApiResponse<StudyPostListResponse> getStudies(
            @Valid @ModelAttribute StudySearchCondition condition,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.onSuccess(
                "스터디 모집글 목록을 조회했습니다.",
                studyService.getStudyPosts(condition, page, size)
        );
    }

    @GetMapping("/{studyId}")
    @Operation(summary = "스터디 모집글 상세 조회")
    public ApiResponse<StudyPostDetailResponse> getStudy(
            @PathVariable Long studyId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Long currentUserId = userDetails == null ? null : userDetails.getUser().getId();
        return ApiResponse.onSuccess(
                "스터디 모집글 상세를 조회했습니다.",
                studyService.getStudyPostDetail(studyId, currentUserId)
        );
    }

    @PostMapping
    @Operation(summary = "스터디 모집글 작성")
    public ApiResponse<StudyPostCreateResponse> createStudy(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody StudyPostCreateRequest request
    ) {
        return ApiResponse.onSuccess(
                "스터디 모집글이 작성되었습니다.",
                studyService.createStudyPost(requireUser(userDetails), request)
        );
    }

    @PostMapping("/{studyId}/applications")
    @Operation(summary = "스터디 지원")
    public ApiResponse<StudyApplicationResponse> applyToStudy(
            @PathVariable Long studyId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "스터디 지원이 완료되었습니다.",
                studyService.applyToStudy(studyId, requireUser(userDetails))
        );
    }

    @PatchMapping("/{studyId}/applications/{applicationId}")
    @Operation(summary = "스터디 지원 상태 변경")
    public ApiResponse<StudyApplicationResponse> updateApplicationStatus(
            @PathVariable Long studyId,
            @PathVariable Long applicationId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody StudyApplicationStatusUpdateRequest request
    ) {
        return ApiResponse.onSuccess(
                "스터디 지원 상태를 변경했습니다.",
                studyService.updateApplicationStatus(studyId, applicationId, requireUser(userDetails), request.status())
        );
    }

    @DeleteMapping("/{studyId}/applications/me")
    @Operation(summary = "내 스터디 지원 취소")
    public ApiResponse<StudyApplicationResponse> cancelMyApplication(
            @PathVariable Long studyId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "스터디 지원을 취소했습니다.",
                studyService.cancelMyApplication(studyId, requireUser(userDetails))
        );
    }

    @PatchMapping("/{studyId}/close")
    @Operation(summary = "스터디 모집 완료 처리")
    public ApiResponse<StudyCloseResponse> closeStudy(
            @PathVariable Long studyId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "스터디 모집 상태를 변경했습니다.",
                studyService.closeStudyPost(studyId, requireUser(userDetails))
        );
    }

    @GetMapping("/applications/me")
    @Operation(summary = "내가 지원한 스터디 목록 조회")
    public ApiResponse<MyStudyApplicationListResponse> getMyApplications(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(required = false) StudyApplicationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.onSuccess(
                "내 스터디 지원 목록을 조회했습니다.",
                studyService.getMyApplications(requireUser(userDetails), status, page, size)
        );
    }

    private Long requireUser(UserDetailsImpl userDetails) {
        if (userDetails == null) {
            throw new GeneralException(GeneralErrorCode.MISSING_AUTH_INFO);
        }
        return userDetails.getUser().getId();
    }
}
