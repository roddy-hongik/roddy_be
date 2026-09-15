package com.roddy.domain.coverletter.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record SaveCoverLetterRequest(
        @NotBlank @Size(max = 255) String title,
        @Positive Long jobPostingId,
        @PositiveOrZero Long version,
        @NotEmpty @Size(max = 20) List<@NotNull @Valid Answer> answers
) {
    public record Answer(
            @NotBlank @Size(max = 1000) String question,
            @NotNull @Size(max = 10000) String answer
    ) {}
}
