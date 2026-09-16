package com.roddy.domain.analysis.service;

import com.roddy.domain.analysis.dto.response.AnalysisReportListResponse;
import com.roddy.domain.analysis.dto.response.AnalysisReportResponse;
import com.roddy.domain.analysis.dto.response.AnalysisReportSummaryResponse;
import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 역량 분석의 바깥쪽 창구.
 *
 * <p>여기서는 트랜잭션을 열지 않는다. "분석 중" 상태를 먼저 커밋해야 뒤이어 돌기 시작하는 분석
 * 스레드가 그 상태를 볼 수 있기 때문이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final AnalysisReportStore analysisReportStore;
    private final AnalysisRunner analysisRunner;

    /**
     * 분석을 시작한다. 리포트는 요청할 때마다 새로 쌓는다.
     *
     * <p>이미 분석 중이면 새로 시작하지 않는다. 같은 사용자를 두 번 돌리면 LLM 비용만 두 배가 된다.
     */
    public AnalysisReportResponse requestAnalysis(Long userId) {
        if (analysisReportStore.isAnalyzing(userId)) {
            log.info("이미 분석 중입니다. userId={}", userId);
            return getReport(userId);
        }

        Long reportId = analysisReportStore.createPending(userId);
        analysisRunner.run(userId, reportId);

        return getReport(userId);
    }

    /** 가장 최근에 요청한 분석. 분석을 요청한 뒤 끝났는지 다시 조회할 때 쓴다. */
    public AnalysisReportResponse getReport(Long userId) {
        return analysisReportStore.findLatest(userId)
                .map(this::toResponse)
                .orElseGet(AnalysisReportResponse::notAnalyzed);
    }

    /** 내 리포트 목록. 끝난 리포트만 최신순으로 싣는다. */
    public AnalysisReportListResponse getMyReports(Long userId) {
        return new AnalysisReportListResponse(analysisReportStore.findCompleted(userId).stream()
                .map(AnalysisReportSummaryResponse::from)
                .toList());
    }

    /**
     * 내 리포트 한 건.
     *
     * <p>남의 리포트는 없는 리포트와 똑같이 404 로 답한다. 403 으로 답하면 그 id 의 리포트가 있다는 것을
     * 알려주게 된다.
     */
    public AnalysisReportResponse getMyReport(Long userId, Long reportId) {
        return analysisReportStore.findReport(userId, reportId)
                .map(this::toResponse)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.ANALYSIS_REPORT_NOT_FOUND));
    }

    private AnalysisReportResponse toResponse(AnalysisReport report) {
        return AnalysisReportResponse.of(
                report,
                analysisReportStore.findCategories(report.getId()),
                analysisReportStore.findStacks(report.getId()));
    }
}
