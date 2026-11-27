package com.roddy.domain.interview.repository;

import com.roddy.domain.interview.entity.InterviewSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
    @EntityGraph(attributePaths = "answers")
    Page<InterviewSession> findAllByUserId(Long userId, Pageable pageable);
    @EntityGraph(attributePaths = "answers")
    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);
}
