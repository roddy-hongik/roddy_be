package com.roddy.domain.roadmap.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SaveRoadMapRequest(
        @NotBlank @Size(max = 255) String title,
        @NotEmpty @Size(min = 3, max = 3) List<@Valid Step> steps
) {
    public record Step(
            @NotBlank @Size(max = 30) String stage,
            @NotBlank String goal,
            @NotEmpty List<@NotBlank @Size(max = 500) String> topics,
            @NotEmpty List<@NotBlank @Size(max = 500) String> outputs
    ) {
    }
}
