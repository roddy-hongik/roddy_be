package com.roddy.domain.interview.dto;

import com.roddy.domain.interview.entity.InterviewAnswer;
import com.roddy.domain.interview.entity.InterviewSession;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

public record InterviewSessionListResponse(List<Item> sessions, int page, int size, long totalElements, int totalPages) {

    public static InterviewSessionListResponse from(Page<InterviewSession> page) {
        return new InterviewSessionListResponse(page.map(session -> new Item(
                        session.getId(), averageScore(session), session.getAnswers().size(), session.getCreatedAt()))
                .getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private static int averageScore(InterviewSession session) {
        return (int) Math.round(session.getAnswers().stream().mapToInt(InterviewAnswer::getScore)
                .average().orElse(0));
    }

    public record Item(Long id, int averageScore, int questionCount, LocalDateTime createdAt) {}
}
