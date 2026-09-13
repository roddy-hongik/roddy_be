package com.roddy.domain.admin.controller;

import com.roddy.domain.admin.dto.ReportedContentResponse;
import com.roddy.domain.admin.service.AdminReportedContentService;
import com.roddy.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/reported-contents")
@RequiredArgsConstructor
@Tag(name = "AdminReportedContent", description = "어드민 신고 콘텐츠 관리 API")
public class AdminReportedContentController {

    private final AdminReportedContentService adminReportedContentService;

    @GetMapping
    @Operation(summary = "신고 콘텐츠 목록", description = "신고가 들어온 글과 댓글을 신고 많은 순으로")
    public ApiResponse<List<ReportedContentResponse>> getReportedContents() {
        return ApiResponse.onSuccess("신고된 콘텐츠를 조회했습니다.", adminReportedContentService.getReportedContents());
    }

    @DeleteMapping("/posts/{postId}")
    @Operation(summary = "신고된 글 삭제", description = "글에 달린 댓글·좋아요·신고도 함께 지운다. reason 은 기록용이다")
    public ApiResponse<Void> removePost(
            @PathVariable Long postId,
            @RequestParam(required = false) String reason) {
        adminReportedContentService.removePost(postId, reason);
        return ApiResponse.onSuccess("신고된 글을 지웠습니다.");
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "신고된 댓글 삭제", description = "대댓글과 그 신고도 함께 지운다. reason 은 기록용이다")
    public ApiResponse<Void> removeComment(
            @PathVariable Long commentId,
            @RequestParam(required = false) String reason) {
        adminReportedContentService.removeComment(commentId, reason);
        return ApiResponse.onSuccess("신고된 댓글을 지웠습니다.");
    }
}
