package com.roddy.domain.roadmap.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveRoadMapRequest(
        @NotBlank @Size(max = 255) String title,
        @NotEmpty @Size(min = 3, max = 3) List<@Valid Step> steps,
        @NotNull List<@NotBlank @Size(max = 100) String> currentSkills,
        @NotEmpty List<@NotBlank @Size(max = 100) String> gapSkills,
        @NotBlank String targetJob,
        String targetCompany
) {
    public SaveRoadMapRequest {
        // 기술은 앞뒤 공백을 걷어내 저장하므로, 길이 제한도 걷어낸 값에 걸리도록 먼저 다듬는다.
        currentSkills = trimmed(currentSkills);
        gapSkills = trimmed(gapSkills);
    }

    private static List<String> trimmed(List<String> values) {
        return values == null ? null : values.stream().map(value -> value == null ? null : value.trim()).toList();
    }

    public record Step(
            @NotBlank @Size(max = 30) String stage,
            @NotBlank String goal,
            @NotEmpty List<@NotBlank @Size(max = 500) String> topics,
            @NotEmpty List<@NotBlank @Size(max = 500) String> outputs
    ) {
    }
}
