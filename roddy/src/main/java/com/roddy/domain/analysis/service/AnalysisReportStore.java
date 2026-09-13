package com.roddy.domain.analysis.service;

import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.analysis.entity.StackDetail;
import com.roddy.domain.analysis.entity.UserStack;
import com.roddy.domain.analysis.enums.AnalysisStatus;
import com.roddy.domain.analysis.repository.AnalysisReportRepository;
import com.roddy.domain.analysis.repository.StackDetailRepository;
import com.roddy.domain.analysis.repository.UserStackRepository;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.enums.StackLevel;
import com.roddy.domain.jobposting.service.TechStackExtractor;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import com.roddy.global.client.analysis.AnalysisAiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 분석 리포트를 저장한다. 분석 실행과 저장을 나눠 두어야 실패해도 상태를 남길 수 있다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisReportStore {

    private final AnalysisReportRepository analysisReportRepository;
    private final UserStackRepository userStackRepository;
    private final StackDetailRepository stackDetailRepository;
    private final UserRepository userRepository;
    private final TechStackExtractor techStackExtractor;

    /**
     * 리포트를 새로 만들어 진행 중으로 둔다. 지난 리포트는 건드리지 않으므로 분석하는 동안에도 볼 수 있다.
     *
     * @return 만든 리포트의 id. 분석이 끝나면 이 리포트를 채운다
     */
    @Transactional
    public Long createPending(Long userId) {
        return analysisReportRepository.save(AnalysisReport.pending(findUser(userId))).getId();
    }

    @Transactional
    public void complete(Long reportId, AnalysisAiResponse response) {
        AnalysisReport report = analysisReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalStateException("분석 리포트가 없습니다. reportId=" + reportId));

        report.complete(response.title(), response.totalScore(), response.summary(),
                response.githubAnalysis(), response.portfolioAnalysis(), LocalDateTime.now());

        saveStacks(report, response.stacks());
    }

    @Transactional
    public void fail(Long reportId, String reason) {
        analysisReportRepository.findById(reportId).ifPresent(report -> report.fail(reason));
    }

    @Transactional(readOnly = true)
    public boolean isAnalyzing(Long userId) {
        return analysisReportRepository.existsByUserIdAndStatus(userId, AnalysisStatus.PENDING);
    }

    /** 가장 최근에 요청한 분석. 진행 중이거나 실패한 것일 수 있다. */
    @Transactional(readOnly = true)
    public Optional<AnalysisReport> findLatest(Long userId) {
        return analysisReportRepository.findFirstByUserIdOrderByIdDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<UserStack> findStacks(Long reportId) {
        return userStackRepository.findAllWithStackDetailByReportId(reportId);
    }

    /**
     * 기술스택은 리포트에 딸려 저장한다. 지난 리포트의 기술은 지우지 않는다. 그 리포트를 다시 열었을 때
     * 그때 무엇을 할 줄 알았는지 보여야 하기 때문이다.
     */
    private void saveStacks(AnalysisReport report, List<AnalysisAiResponse.AnalyzedStack> stacks) {
        dedupeByName(stacks).forEach((name, stack) -> {
            StackDetail detail = stackDetailRepository.findByStackName(name)
                    .orElseGet(() -> stackDetailRepository.save(StackDetail.ofName(name)));

            userStackRepository.save(UserStack.create(
                    report.getUser(), detail, report, toStackLevel(stack.level()), stack.score(), stack.description()));
        });
    }

    /**
     * 표준 이름 → 기술. 같은 기술을 두 번 내놓을 수 있어서 표준 이름이 겹치면 앞의 것만 남긴다.
     * 사전에 없는 기술은 공고와 이어지지 않으므로 뺀다.
     */
    private Map<String, AnalysisAiResponse.AnalyzedStack> dedupeByName(
            List<AnalysisAiResponse.AnalyzedStack> stacks) {
        Map<String, AnalysisAiResponse.AnalyzedStack> unique = new LinkedHashMap<>();

        for (AnalysisAiResponse.AnalyzedStack stack : stacks) {
            String name = techStackExtractor.canonicalize(stack.name());
            if (name != null) {
                unique.putIfAbsent(name, stack);
            }
        }
        return unique;
    }

    /** 모르는 값이 오면 비워 둔다. 숙련도 단계 하나 때문에 기술을 통째로 버리지 않는다. */
    private StackLevel toStackLevel(String level) {
        if (level == null || level.isBlank()) {
            return null;
        }

        try {
            return StackLevel.valueOf(level.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("알 수 없는 숙련도 단계입니다: {}", level);
            return null;
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }
}
