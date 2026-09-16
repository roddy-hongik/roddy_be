package com.roddy.domain.dashboard.dto.response;

import com.roddy.domain.analysis.dto.response.AnalysisReportResponse;
import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.analysis.entity.AnalysisReportCategory;
import com.roddy.domain.analysis.entity.UserStack;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.jobposting.dto.response.JobPostingListItemResponse;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 메인 대시보드.
 *
 * @param desiredJob      지금 희망 직무
 * @param reportId        대시보드를 그린 리포트. 가장 최근에 끝난 리포트이고, 끝난 분석이 없으면 비어 있다
 * @param categories      레이더 차트. 리포트의 평가 축별 점수와 해석, 그 축에 속한 기술
 * @param techKeywords    리포트가 찾아낸 기술 이름. 숙련도 점수가 높은 순
 * @param bestMatchRate   추천 공고 중 가장 높은 매칭률. 추천할 공고가 없으면 비어 있다
 * @param recommendedJobs 모집 중인 공고 중 매칭률이 높은 공고. 공고 목록과 같은 모양이다
 */
public record DashboardSummaryResponse(
        String userName,
        DesiredJob desiredJob,
        String desiredCompany,
        Long reportId,
        LocalDateTime analyzedAt,
        List<AnalysisReportResponse.CategoryResponse> categories,
        List<String> techKeywords,
        Integer bestMatchRate,
        List<JobPostingListItemResponse> recommendedJobs
) {

    public static DashboardSummaryResponse of(User user,
                                              String desiredCompany,
                                              AnalysisReport report,
                                              List<AnalysisReportCategory> categories,
                                              List<UserStack> stacks,
                                              List<JobPostingListItemResponse> recommendedJobs) {
        return new DashboardSummaryResponse(
                user.getNickname(),
                user.getDesiredJob(),
                desiredCompany,
                report.getId(),
                report.getAnalyzedAt(),
                categories.stream()
                        .map(category -> AnalysisReportResponse.CategoryResponse.of(category, stacks))
                        .toList(),
                stacks.stream()
                        .sorted(Comparator.comparingInt(UserStack::getScore).reversed())
                        .map(stack -> stack.getStackDetail().getStackName())
                        .toList(),
                recommendedJobs.isEmpty() ? null : recommendedJobs.get(0).matchRate(),
                recommendedJobs
        );
    }

    /** 아직 끝난 분석이 없다. 점수와 추천을 지어내지 않고 비워 둔다. */
    public static DashboardSummaryResponse notAnalyzed(User user, String desiredCompany) {
        return new DashboardSummaryResponse(
                user.getNickname(),
                user.getDesiredJob(),
                desiredCompany,
                null,
                null,
                List.of(),
                List.of(),
                null,
                List.of()
        );
    }
}
