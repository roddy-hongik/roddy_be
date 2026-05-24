package com.roddy.domain.community.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateCommunityCommentRequest(
        @NotBlank
        String content
) {
}
