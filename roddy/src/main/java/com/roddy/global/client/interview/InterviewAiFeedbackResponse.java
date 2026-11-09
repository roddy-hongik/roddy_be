package com.roddy.global.client.interview;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

public record InterviewAiFeedbackResponse(List<Feedback> feedbacks) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Feedback(String id, int score, String feedback) {
    }
}
