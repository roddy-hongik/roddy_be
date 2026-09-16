package com.roddy.domain.interview.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

/** 질문 한 개에 대한 답변과 AI 피드백. 세션에 속하는 값이라 배열 순서를 DB에도 보존한다. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewAnswer {

    // 채점 포인트는 짧은 구문이라 "|"가 섞일 일이 거의 없어 별도 테이블 없이 한 컬럼에 이어 붙인다.
    private static final String KEY_POINT_DELIMITER = "|";

    @Column(name = "question_key", nullable = false, length = 50)
    private String questionKey;
    @Column(name = "question", nullable = false, length = 1000)
    private String question;
    @Column(name = "intent", nullable = false, length = 500)
    private String intent;
    @Column(name = "key_points", nullable = false, length = 1000)
    private String keyPoints;
    @Column(name = "answer", nullable = false, columnDefinition = "TEXT")
    private String answer;
    @Column(name = "feedback", nullable = false, columnDefinition = "TEXT")
    private String feedback;
    @Column(name = "score", nullable = false)
    private int score;

    public InterviewAnswer(String questionKey, String question, String intent, List<String> keyPoints,
                            String answer, String feedback, int score) {
        this.questionKey = questionKey.trim();
        this.question = question.trim();
        this.intent = intent.trim();
        this.keyPoints = String.join(KEY_POINT_DELIMITER, keyPoints);
        // 작성 중인 줄바꿈과 공백은 사용자가 입력한 그대로 저장한다.
        this.answer = answer;
        this.feedback = feedback.trim();
        this.score = score;
    }

    public List<String> getKeyPointList() {
        return Arrays.asList(keyPoints.split("\\" + KEY_POINT_DELIMITER));
    }
}
