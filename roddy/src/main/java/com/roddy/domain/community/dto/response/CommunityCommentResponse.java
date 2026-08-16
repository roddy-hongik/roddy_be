package com.roddy.domain.community.dto.response;

import java.time.LocalDate;

public record CommunityCommentResponse(
        Long id,
        String content,
        String authorName,
        Long parentId,
        int depth,
        LocalDate createdAt
) {
}
