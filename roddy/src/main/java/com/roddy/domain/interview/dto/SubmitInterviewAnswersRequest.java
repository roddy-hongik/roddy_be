package com.roddy.domain.interview.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record SubmitInterviewAnswersRequest(
        @NotEmpty @Size(min = 3, max = 3) List<@NotNull @Valid Answer> answers
) {
    public record Answer(
            @NotBlank @Size(max = 50) String id,
            @NotBlank @Size(max = 1000) String question,
            @NotBlank @Size(max = 500) String intent,
            @NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 200) String> keyPoints,
            @NotBlank @Size(max = 4000) String answer
    ) {}
}
