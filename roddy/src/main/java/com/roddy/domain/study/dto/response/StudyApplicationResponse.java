package com.roddy.domain.study.dto.response;

public record StudyApplicationResponse(
        Long applicationId,
        String status,
        String statusDisplayName,
        int applicantCount
) {
}
