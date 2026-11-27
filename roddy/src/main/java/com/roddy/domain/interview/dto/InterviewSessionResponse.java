package com.roddy.domain.interview.dto;

import com.roddy.domain.interview.entity.InterviewAnswer;
import com.roddy.domain.interview.entity.InterviewSession;

import java.time.LocalDateTime;
import java.util.List;

public record InterviewSessionResponse(Long id, List<Answer> answers, LocalDateTime createdAt) {

    public static InterviewSessionResponse from(InterviewSession session) {
        return new InterviewSessionResponse(session.getId(),
                session.getAnswers().stream().map(Answer::from).toList(), session.getCreatedAt());
    }

    public record Answer(String id, String question, String intent, List<String> keyPoints,
                          String answer, String feedback, int score) {
        static Answer from(InterviewAnswer answer) {
            return new Answer(answer.getQuestionKey(), answer.getQuestion(), answer.getIntent(),
                    answer.getKeyPointList(), answer.getAnswer(), answer.getFeedback(), answer.getScore());
        }
    }
}
