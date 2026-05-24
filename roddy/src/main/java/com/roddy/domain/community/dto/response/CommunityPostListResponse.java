package com.roddy.domain.community.dto.response;

import java.util.List;

public record CommunityPostListResponse(
        List<CommunityPostListItemResponse> posts,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
