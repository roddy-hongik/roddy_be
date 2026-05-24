package com.roddy.domain.study.dto.response;

public record StudyCloseResponse(
        Long id,
        String status,
        String statusDisplayName
) {
}
