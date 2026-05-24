package com.roddy.domain.study.dto.response;

import java.time.LocalDateTime;

public record MyStudyApplicationResponse(
        Long applicationId,
        Long studyId,
        String title,
        String mode,
        String modeDisplayName,
        String location,
        LocalDateTime scheduledAt,
        int capacity,
        int applicantCount,
        String recruitStatus,
        String recruitStatusDisplayName,
        String applicationStatus,
        String applicationStatusDisplayName,
        LocalDateTime appliedAt
) {
}
