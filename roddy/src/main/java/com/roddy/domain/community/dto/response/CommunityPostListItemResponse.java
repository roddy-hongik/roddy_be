package com.roddy.domain.community.dto.response;

import java.time.LocalDate;

public record CommunityPostListItemResponse(
        Long id,
        String tag,
        String tagDisplayName,
        String title,
        String authorName,
        LocalDate createdAt,
        int viewCount,
        int likeCount
) {
}
