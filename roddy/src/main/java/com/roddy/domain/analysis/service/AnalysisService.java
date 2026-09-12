package com.roddy.domain.analysis.service;

import com.roddy.domain.analysis.dto.response.AnalysisReportResponse;
import com.roddy.domain.analysis.entity.AnalysisReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

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
     * 분석을 시작한다.
     *
     * <p>이미 분석 중이면 새로 시작하지 않는다. 같은 사용자를 두 번 돌리면 LLM 비용만 두 배가 되고
     * 결과는 하나만 남는다.
     */
    public AnalysisReportResponse requestAnalysis(Long userId) {
        Optional<AnalysisReport> current = analysisReportStore.findReport(userId);
        if (current.isPresent() && current.get().isPending()) {
            log.info("이미 분석 중입니다. userId={}", userId);
            return getReport(userId);
        }

        analysisReportStore.markPending(userId);
        analysisRunner.run(userId);

        return getReport(userId);
    }

    public AnalysisReportResponse getReport(Long userId) {
        return analysisReportStore.findReport(userId)
                .map(report -> AnalysisReportResponse.of(report, analysisReportStore.findStacks(userId)))
                .orElseGet(AnalysisReportResponse::notAnalyzed);
    }
}
