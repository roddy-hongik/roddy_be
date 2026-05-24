package com.roddy.domain.study.dto.response;

import java.util.List;

public record StudyPostListResponse(
        List<StudyPostListItemResponse> studies,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
