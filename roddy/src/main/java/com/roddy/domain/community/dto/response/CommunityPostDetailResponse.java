package com.roddy.domain.community.dto.response;

import java.time.LocalDate;
import java.util.List;

public record CommunityPostDetailResponse(
        Long id,
        String tag,
        String tagDisplayName,
        String title,
        String content,
        String authorName,
        LocalDate createdAt,
        int viewCount,
        int likeCount,
        boolean liked,
        List<String> imageUrls,
        List<CommunityCommentResponse> comments
) {
}
