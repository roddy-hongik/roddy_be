package com.roddy.domain.interview.dto;

import com.roddy.global.client.interview.InterviewAiResponse;

import java.util.List;

public record InterviewQuestionsResponse(List<Question> questions) {

    public static InterviewQuestionsResponse from(InterviewAiResponse response) {
        return new InterviewQuestionsResponse(response.questions().stream()
                .map(question -> new Question(
                        question.id(), question.question(), question.intent(), question.keyPoints()))
                .toList());
    }

    public record Question(String id, String question, String intent, List<String> keyPoints) {
    }
}
