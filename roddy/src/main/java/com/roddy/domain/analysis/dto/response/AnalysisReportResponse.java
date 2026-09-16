package com.roddy.domain.analysis.dto.response;

import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.analysis.entity.AnalysisReportCategory;
import com.roddy.domain.analysis.entity.UserStack;
import com.roddy.domain.analysis.enums.AnalysisStatus;
import com.roddy.domain.enums.DesiredJob;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 내 역량 분석 리포트.
 *
 * @param id            리포트 id. 아직 분석을 요청한 적이 없으면 비어 있다.
 * @param status        분석 중이면 PENDING. 내용 필드는 완료 전까지 비어 있다.
 * @param desiredJob    분석을 요청한 당시의 희망 직무. 평가 축이 직무마다 다르다.
 * @param failureReason 실패했을 때만 채워진다.
 * @param categories    평가 축별 점수. 축을 정하지 않은 직무면 비어 있다.
 */
public record AnalysisReportResponse(
        Long id,
        AnalysisStatus status,
        DesiredJob desiredJob,
        String title,
        int totalScore,
        String summary,
        String githubAnalysis,
        String portfolioAnalysis,
        String failureReason,
        LocalDateTime analyzedAt,
        List<CategoryResponse> categories,
        List<AnalyzedStackResponse> stacks
) {

    /**
     * 평가 축 하나.
     *
     * @param stacks 이 축에 속한 기술 이름
     */
    public record CategoryResponse(String code, String name, String description, int score,
                                   String interpretation, List<String> stacks) {

        public static CategoryResponse of(AnalysisReportCategory category, List<UserStack> reportStacks) {
            return new CategoryResponse(
                    category.getCode(),
                    category.getName(),
                    category.getDescription(),
                    category.getScore(),
                    category.getInterpretation(),
                    reportStacks.stream()
                            .filter(stack -> category.getCode().equals(stack.getCategoryCode()))
                            .map(stack -> stack.getStackDetail().getStackName())
                            .toList()
            );
        }
    }

    /**
     * 분석이 찾아낸 기술 하나.
     *
     * @param category         속한 평가 축의 code. 없으면 비어 있다.
     * @param foundInGithub    깃허브 저장소에서 근거를 찾았는지
     * @param foundInPortfolio 포트폴리오에서 근거를 찾았는지
     */
    public record AnalyzedStackResponse(String name, int score, String level, String description,
                                        String category, boolean foundInGithub, boolean foundInPortfolio) {

        public static AnalyzedStackResponse from(UserStack userStack) {
            return new AnalyzedStackResponse(
                    userStack.getStackDetail().getStackName(),
                    userStack.getScore(),
                    userStack.getStackLevel() == null ? null : userStack.getStackLevel().name(),
                    userStack.getDescription(),
                    userStack.getCategoryCode(),
                    userStack.isFoundInGithub(),
                    userStack.isFoundInPortfolio()
            );
        }
    }

    public static AnalysisReportResponse of(AnalysisReport report,
                                            List<AnalysisReportCategory> categories,
                                            List<UserStack> stacks) {
        return new AnalysisReportResponse(
                report.getId(),
                report.getStatus(),
                report.getDesiredJob(),
                report.getTitle(),
                report.getTotalScore(),
                report.getSummary(),
                report.getGithubAnalysis(),
                report.getPortfolioAnalysis(),
                report.getFailureReason(),
                report.getAnalyzedAt(),
                categories.stream().map(category -> CategoryResponse.of(category, stacks)).toList(),
                stacks.stream().map(AnalyzedStackResponse::from).toList()
        );
    }

    /** 아직 한 번도 분석하지 않은 사용자. */
    public static AnalysisReportResponse notAnalyzed() {
        return new AnalysisReportResponse(
                null, null, null, null, 0, null, null, null, null, null, List.of(), List.of());
    }
}
