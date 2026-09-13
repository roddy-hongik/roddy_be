package com.roddy.domain.analysis.dto.response;

import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.enums.DesiredJob;

import java.time.LocalDateTime;

/**
 * 내 리포트 목록의 한 줄. 목록에는 무거운 본문(깃허브·포트폴리오 분석, 축별 해석, 기술스택)을 싣지 않는다.
 *
 * @param desiredJob 분석을 요청한 당시의 희망 직무
 */
public record AnalysisReportSummaryResponse(
        Long id,
        DesiredJob desiredJob,
        String title,
        int totalScore,
        String summary,
        LocalDateTime analyzedAt
) {

    public static AnalysisReportSummaryResponse from(AnalysisReport report) {
        return new AnalysisReportSummaryResponse(
                report.getId(),
                report.getDesiredJob(),
                report.getTitle(),
                report.getTotalScore(),
                report.getSummary(),
                report.getAnalyzedAt()
        );
    }
}
