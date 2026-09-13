package com.roddy.global.client.interview;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InterviewAiRequest(
        List<String> currentSkills,
        List<String> gapSkills,
        String targetJob,
        String targetCompany
) {
}
