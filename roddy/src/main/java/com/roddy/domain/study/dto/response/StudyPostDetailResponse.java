package com.roddy.domain.study.dto.response;

import java.time.LocalDateTime;

public record StudyPostDetailResponse(
        Long id,
        String title,
        String content,
        String authorName,
        LocalDateTime createdAt,
        String mode,
        String modeDisplayName,
        String location,
        LocalDateTime scheduledAt,
        int capacity,
        int applicantCount,
        String status,
        String statusDisplayName,
        String myApplicationStatus,
        String myApplicationStatusDisplayName,
        boolean isAuthor
) {
}
