package com.roddy.global.client.interview;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

public record InterviewAiResponse(List<Question> questions) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Question(String id, String question, String intent, List<String> keyPoints) {
    }
}
