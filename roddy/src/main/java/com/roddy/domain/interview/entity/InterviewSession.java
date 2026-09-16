package com.roddy.domain.interview.entity;

import com.roddy.domain.BaseEntity;
import com.roddy.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** 모의면접 한 회차의 결과. 질문·답변·AI 피드백을 함께 담아 지난 회차를 그대로 다시 볼 수 있게 한다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "interview_sessions", indexes = @Index(name = "idx_interview_session_user_created", columnList = "user_id, created_at"))
public class InterviewSession extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interview_session_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ElementCollection
    @CollectionTable(name = "interview_session_answers", joinColumns = @JoinColumn(name = "interview_session_id"))
    @OrderColumn(name = "answer_order")
    private List<InterviewAnswer> answers = new ArrayList<>();

    public static InterviewSession create(User user, List<InterviewAnswer> answers) {
        InterviewSession session = new InterviewSession();
        session.user = user;
        session.answers.addAll(answers);
        return session;
    }
}
