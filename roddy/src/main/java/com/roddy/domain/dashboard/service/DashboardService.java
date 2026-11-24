package com.roddy.domain.dashboard.service;

import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.analysis.service.AnalysisReportStore;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.dashboard.dto.response.DashboardSummaryResponse;
import com.roddy.domain.jobposting.service.JobPostingService;
import com.roddy.domain.mypage.entity.DesiredCompany;
import com.roddy.domain.mypage.repository.DesiredCompanyRepository;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 메인 대시보드에 흩어진 결과를 모은다.
 *
 * <p>여기서 새로 계산하는 점수는 없다. 리포트는 분석 도메인이, 매칭률은 채용공고 도메인이 이미 내고 있다.
 * 같은 숫자를 대시보드에서 따로 계산하면 화면마다 숫자가 달라진다.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** 대시보드에 보여줄 추천 공고 수. */
    private static final int RECOMMENDED_JOB_COUNT = 3;

    private final UserRepository userRepository;
    private final DesiredCompanyRepository desiredCompanyRepository;
    private final AnalysisReportStore analysisReportStore;
    private final JobPostingService jobPostingService;

    public DashboardSummaryResponse getSummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        String desiredCompany = desiredCompanyRepository.findByUserId(userId)
                .map(DesiredCompany::getDesiredCompany)
                .orElse(null);

        return analysisReportStore.findLatestCompleted(userId)
                .map(report -> summarize(user, desiredCompany, report))
                .orElseGet(() -> DashboardSummaryResponse.notAnalyzed(user, desiredCompany));
    }

    /** 다시 분석하는 중이어도 가장 최근에 끝난 리포트로 그린다. 분석 중인 리포트는 아직 비어 있다. */
    private DashboardSummaryResponse summarize(User user, String desiredCompany, AnalysisReport report) {
        return DashboardSummaryResponse.of(
                user,
                desiredCompany,
                report,
                analysisReportStore.findCategories(report.getId()),
                analysisReportStore.findStacks(report.getId()),
                jobPostingService.getRecommendedJobs(user.getId(), user.getDesiredJob(), desiredCompany, RECOMMENDED_JOB_COUNT)
        );
    }

}
