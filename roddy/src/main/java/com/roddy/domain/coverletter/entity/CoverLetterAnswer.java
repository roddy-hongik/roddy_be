package com.roddy.domain.coverletter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 문항은 문서에 속하는 값이다. 배열 순서를 DB에도 보존한다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoverLetterAnswer {
    @Column(name = "question", nullable = false, length = 1000)
    private String question;
    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    private String answer;

    public CoverLetterAnswer(String question, String answer) {
        this.question = question.trim();
        // 작성 중인 줄바꿈과 공백은 사용자가 입력한 그대로 저장한다.
        this.answer = answer;
    }
}
