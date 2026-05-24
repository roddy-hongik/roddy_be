package com.roddy.domain.study.dto.response;

import java.util.List;

public record MyStudyApplicationListResponse(
        List<MyStudyApplicationResponse> applications,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
