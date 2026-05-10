package com.roddy.domain;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


    @Column(nullable = false)
    private String question;   // 질문

    @Column(columnDefinition = "TEXT", nullable = false)
    private String answer;   // 답변


    public static Question create(User user, String question, String answer) {
        return Question.builder()
                .user(user)
                .question(question)
                .answer(answer)
                .build();
    }

    public void update(String question, String answer) {
        this.question = question;
        this.answer = answer;

    }
}
