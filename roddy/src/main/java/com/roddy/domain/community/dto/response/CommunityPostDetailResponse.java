package com.roddy.domain.community.dto.response;

import java.time.LocalDate;
import java.util.List;

public record CommunityPostDetailResponse(
        Long id,
        String postCategory,
        String postCategoryDisplayName,
        String jobCategory,
        String jobCategoryDisplayName,
        String title,
        String content,
        String authorName,
        LocalDate createdAt,
        int viewCount,
        int likeCount,
        boolean liked,
        String company,
        String position,
        List<String> techStacks,
        List<String> imageUrls,
        List<CommunityCommentResponse> comments
) {
}
