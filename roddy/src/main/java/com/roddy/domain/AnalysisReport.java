package com.roddy.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "analysis_reports")
public class AnalysisReport extends BaseEntity { // createdAt을 lastAnalyzedAt 대신 사용 가능


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_report_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;


    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int totalScore;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String githubAnalysis;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String portfolioAnalysis;

    public static AnalysisReport create(User user, String title, int totalScore,
                                        String summary, String githubAnalysis,
                                        String portfolioAnalysis) {
        return AnalysisReport.builder()
                .user(user)
                .title(title)
                .totalScore(totalScore)
                .summary(summary)
                .githubAnalysis(githubAnalysis)
                .portfolioAnalysis(portfolioAnalysis)
                .build();
    }

    public void update(String title, int totalScore, String summary,
                       String githubAnalysis, String portfolioAnalysis) {
        this.title = title;
        this.totalScore = totalScore;
        this.summary = summary;
        this.githubAnalysis = githubAnalysis;
        this.portfolioAnalysis = portfolioAnalysis;
    }
}