package com.roddy.domain.community.dto.response;

import java.time.LocalDateTime;

public record CommunityCommentResponse(
        Long id,
        String author,
        String content,
        Long parentId,
        int depth,
        LocalDateTime createdAt
) {
}
