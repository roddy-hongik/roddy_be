package com.roddy.domain.community.dto.response;

public record TogglePostLikeResponse(
        boolean liked,
        int likeCount
) {
}
