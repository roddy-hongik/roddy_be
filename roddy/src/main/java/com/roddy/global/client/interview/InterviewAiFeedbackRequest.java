package com.roddy.global.client.interview;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record InterviewAiFeedbackRequest(List<Answer> answers) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Answer(String id, String question, String intent, List<String> keyPoints, String answer) {
    }
}
