package com.roddy.domain.study.dto.request;

import com.roddy.domain.study.enums.StudyApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record StudyApplicationStatusUpdateRequest(
        @NotNull
        StudyApplicationStatus status
) {
}
