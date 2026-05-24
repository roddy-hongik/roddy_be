package com.roddy.domain.study.dto.request;

import com.roddy.domain.study.enums.StudyMode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record StudyPostCreateRequest(
        @NotBlank String title,
        @NotBlank String content,
        @NotNull StudyMode mode,
        String location,
        @NotNull LocalDateTime scheduledAt,
        @NotNull @Min(1) Integer capacity
) {
}
