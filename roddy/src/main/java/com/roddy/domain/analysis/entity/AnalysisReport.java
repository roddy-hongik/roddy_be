package com.roddy.domain.analysis.entity;

import com.roddy.domain.BaseEntity;
import com.roddy.domain.analysis.enums.AnalysisStatus;
import com.roddy.domain.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자 한 명의 역량 분석 리포트.
 *
 * <p>사용자당 하나만 두고 다시 분석하면 덮어쓴다. 분석은 깃허브를 훑고 LLM 을 부르느라 오래 걸리므로
 * 먼저 {@link AnalysisStatus#PENDING} 으로 만들어 두고, 끝나면 내용을 채운다. 그래서 내용 필드는
 * 완료되기 전까지 비어 있다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "analysis_reports")
public class AnalysisReport extends BaseEntity {

    private static final int FAILURE_REASON_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_report_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    private String title;

    @Column(nullable = false)
    private int totalScore;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String githubAnalysis;

    @Column(columnDefinition = "TEXT")
    private String portfolioAnalysis;

    /** 분석이 실패한 이유. 사용자가 무엇을 고쳐야 하는지 알 수 있어야 한다. */
    @Column(length = FAILURE_REASON_MAX_LENGTH)
    private String failureReason;

    /** 마지막으로 분석을 끝낸 시각. */
    private LocalDateTime analyzedAt;

    public static AnalysisReport pending(User user) {
        return AnalysisReport.builder()
                .user(user)
                .status(AnalysisStatus.PENDING)
                .totalScore(0)
                .build();
    }

    /** 다시 분석할 때. 이전 내용은 남겨 두어 분석 중에도 지난 리포트를 볼 수 있게 한다. */
    public void markPending() {
        this.status = AnalysisStatus.PENDING;
        this.failureReason = null;
    }

    public void complete(String title, int totalScore, String summary,
                         String githubAnalysis, String portfolioAnalysis, LocalDateTime analyzedAt) {
        this.status = AnalysisStatus.COMPLETED;
        this.title = title;
        this.totalScore = totalScore;
        this.summary = summary;
        this.githubAnalysis = githubAnalysis;
        this.portfolioAnalysis = portfolioAnalysis;
        this.failureReason = null;
        this.analyzedAt = analyzedAt;
    }

    public void fail(String reason) {
        this.status = AnalysisStatus.FAILED;
        this.failureReason = truncate(reason);
    }

    public boolean isPending() {
        return this.status == AnalysisStatus.PENDING;
    }

    /** 예외 메시지는 길이를 알 수 없으므로 컬럼 크기에 맞춰 자른다. */
    private static String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= FAILURE_REASON_MAX_LENGTH
                ? reason
                : reason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
