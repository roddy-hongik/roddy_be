package com.roddy.domain.study.dto.response;

import java.time.LocalDateTime;

public record StudyPostListItemResponse(
        Long id,
        String title,
        String contentPreview,
        String mode,
        String modeDisplayName,
        String location,
        LocalDateTime scheduledAt,
        int capacity,
        int applicantCount,
        String status,
        String statusDisplayName
) {
}
