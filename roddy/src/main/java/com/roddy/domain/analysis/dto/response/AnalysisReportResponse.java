package com.roddy.domain.analysis.dto.response;

import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.analysis.entity.UserStack;
import com.roddy.domain.analysis.enums.AnalysisStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 역량 분석 리포트.
 *
 * @param status        분석 중이면 PENDING. 내용 필드는 완료 전까지 비어 있다.
 * @param failureReason 실패했을 때만 채워진다.
 */
public record AnalysisReportResponse(
        AnalysisStatus status,
        String title,
        int totalScore,
        String summary,
        String githubAnalysis,
        String portfolioAnalysis,
        String failureReason,
        LocalDateTime analyzedAt,
        List<AnalyzedStackResponse> stacks
) {

    public record AnalyzedStackResponse(String name, int score, String level, String description) {

        public static AnalyzedStackResponse from(UserStack userStack) {
            return new AnalyzedStackResponse(
                    userStack.getStackDetail().getStackName(),
                    userStack.getScore(),
                    userStack.getStackLevel() == null ? null : userStack.getStackLevel().name(),
                    userStack.getDescription()
            );
        }
    }

    public static AnalysisReportResponse of(AnalysisReport report, List<UserStack> stacks) {
        return new AnalysisReportResponse(
                report.getStatus(),
                report.getTitle(),
                report.getTotalScore(),
                report.getSummary(),
                report.getGithubAnalysis(),
                report.getPortfolioAnalysis(),
                report.getFailureReason(),
                report.getAnalyzedAt(),
                stacks.stream().map(AnalyzedStackResponse::from).toList()
        );
    }

    /** 아직 한 번도 분석하지 않은 사용자. */
    public static AnalysisReportResponse notAnalyzed() {
        return new AnalysisReportResponse(null, null, 0, null, null, null, null, null, List.of());
    }
}
