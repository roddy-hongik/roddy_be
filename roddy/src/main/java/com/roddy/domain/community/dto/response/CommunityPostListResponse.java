package com.roddy.domain.community.dto.response;

import java.util.List;

public record CommunityPostListResponse(
        List<CommunityPostListItemResponse> posts
) {
}
