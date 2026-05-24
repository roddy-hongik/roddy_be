package com.roddy.domain.community.controller;

import com.roddy.domain.community.dto.request.CreateCommunityCommentRequest;
import com.roddy.domain.community.dto.request.CreateCommunityPostRequest;
import com.roddy.domain.community.dto.response.CommunityCommentResponse;
import com.roddy.domain.community.dto.response.CommunityPostDetailResponse;
import com.roddy.domain.community.dto.response.CommunityPostListResponse;
import com.roddy.domain.community.dto.response.CreateCommunityPostResponse;
import com.roddy.domain.community.dto.response.ReportPostResponse;
import com.roddy.domain.community.dto.response.TogglePostLikeResponse;
import com.roddy.domain.community.enums.CommunityTag;
import com.roddy.domain.community.service.CommunityPostService;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/community/posts")
@RequiredArgsConstructor
@Tag(name = "Community", description = "커뮤니티 게시글 API")
public class CommunityPostController {

    private final CommunityPostService communityPostService;

    @GetMapping
    @Operation(summary = "커뮤니티 게시글 목록 조회")
    public ApiResponse<CommunityPostListResponse> getPosts(
            @RequestParam(required = false) CommunityTag tag,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.onSuccess(
                "커뮤니티 게시글 목록을 조회했습니다.",
                communityPostService.getPosts(tag, page, size)
        );
    }

    @GetMapping("/{postId}")
    @Operation(summary = "커뮤니티 게시글 상세 조회")
    public ApiResponse<CommunityPostDetailResponse> getPost(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        Long userId = userDetails == null ? null : userDetails.getUser().getId();
        return ApiResponse.onSuccess(
                "커뮤니티 게시글 상세를 조회했습니다.",
                communityPostService.getPost(postId, userId)
        );
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "커뮤니티 게시글 작성")
    public ApiResponse<CreateCommunityPostResponse> createPost(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @ModelAttribute CreateCommunityPostRequest request
    ) {
        return ApiResponse.onSuccess(
                "게시글이 작성되었습니다.",
                communityPostService.createPost(requireUser(userDetails), request)
        );
    }

    @PostMapping("/{postId}/like")
    @Operation(summary = "게시글 좋아요 토글")
    public ApiResponse<TogglePostLikeResponse> toggleLike(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "좋아요 상태가 변경되었습니다.",
                communityPostService.toggleLike(postId, requireUser(userDetails))
        );
    }

    @PostMapping("/{postId}/report")
    @Operation(summary = "게시글 신고")
    public ApiResponse<ReportPostResponse> reportPost(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserDetailsImpl userDetails
    ) {
        return ApiResponse.onSuccess(
                "게시글이 신고되었습니다.",
                communityPostService.reportPost(postId, requireUser(userDetails))
        );
    }

    @PostMapping("/{postId}/comments")
    @Operation(summary = "댓글 작성")
    public ApiResponse<CommunityCommentResponse> createComment(
            @PathVariable Long postId,
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @Valid @RequestBody CreateCommunityCommentRequest request
    ) {
        return ApiResponse.onSuccess(
                "댓글이 작성되었습니다.",
                communityPostService.createComment(postId, requireUser(userDetails), request)
        );
    }

    private Long requireUser(UserDetailsImpl userDetails) {
        if (userDetails == null) {
            throw new GeneralException(GeneralErrorCode.MISSING_AUTH_INFO);
        }
        return userDetails.getUser().getId();
    }
}
