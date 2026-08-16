package com.roddy.domain.study.dto.response;

import java.time.LocalDateTime;

public record StudyApplicantSummaryResponse(
        Long applicationId,
        Long applicantId,
        String applicantName,
        String status,
        String statusDisplayName,
        LocalDateTime appliedAt
) {
}
