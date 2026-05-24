package com.roddy.domain.community.dto.response;

import java.time.LocalDate;

public record CommunityPostListItemResponse(
        Long id,
        String postCategory,
        String postCategoryDisplayName,
        String jobCategory,
        String jobCategoryDisplayName,
        String title,
        String authorName,
        LocalDate createdAt,
        int viewCount,
        int likeCount,
        String company,
        String position,
        java.util.List<String> techStacks
) {
}
