package com.roddy.domain.community.controller;

import com.roddy.domain.community.dto.response.ReportPostResponse;
import com.roddy.domain.community.service.CommunityPostService;
import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import com.roddy.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community/comments")
@RequiredArgsConstructor
@Tag(name = "Community Comment", description = "커뮤니티 댓글 액션 API")
public class CommunityCommentController {

    private final CommunityPostService communityPostService;

    @DeleteMapping("/{commentId}")
    @Operation(summary = "댓글 삭제")
    public ApiResponse<Void> deleteComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        communityPostService.deleteComment(commentId, requireUser(userDetails));
        return ApiResponse.onSuccess("댓글이 삭제되었습니다.");
    }

    @PostMapping("/{commentId}/report")
    @Operation(summary = "댓글 신고")
    public ApiResponse<ReportPostResponse> reportComment(
            @PathVariable Long commentId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "댓글이 신고되었습니다.",
                communityPostService.reportComment(commentId, requireUser(userDetails))
        );
    }

    private Long requireUser(UserDetailsImpl userDetails) {
        if (userDetails == null) {
            throw new GeneralException(GeneralErrorCode.MISSING_AUTH_INFO);
        }
        return userDetails.getUser().getId();
    }
}
