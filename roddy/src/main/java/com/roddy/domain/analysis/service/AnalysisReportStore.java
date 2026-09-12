package com.roddy.domain.analysis.service;

import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.analysis.entity.StackDetail;
import com.roddy.domain.analysis.entity.UserStack;
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

    /** 이전 내용은 지우지 않는다. 다시 분석하는 동안에도 지난 리포트를 볼 수 있어야 한다. */
    @Transactional
    public void markPending(Long userId) {
        analysisReportRepository.findByUserId(userId)
                .ifPresentOrElse(
                        AnalysisReport::markPending,
                        () -> analysisReportRepository.save(AnalysisReport.pending(findUser(userId))));
    }

    @Transactional
    public void complete(Long userId, AnalysisAiResponse response) {
        User user = findUser(userId);
        AnalysisReport report = analysisReportRepository.findByUserId(userId)
                .orElseGet(() -> analysisReportRepository.save(AnalysisReport.pending(user)));

        report.complete(response.title(), response.totalScore(), response.summary(),
                response.githubAnalysis(), response.portfolioAnalysis(), LocalDateTime.now());

        replaceUserStacks(user, report, response.stacks());
    }

    @Transactional
    public void fail(Long userId, String reason) {
        analysisReportRepository.findByUserId(userId).ifPresent(report -> report.fail(reason));
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisReport> findReport(Long userId) {
        return analysisReportRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<UserStack> findStacks(Long userId) {
        return userStackRepository.findAllWithStackDetailByUserId(userId);
    }

    /**
     * 분석할 때마다 기술스택을 통째로 갈아끼운다. 예전 분석에서 잡혔던 기술이 남아 있으면 지금 실력과
     * 어긋나기 때문이다.
     */
    private void replaceUserStacks(User user, AnalysisReport report,
                                   List<AnalysisAiResponse.AnalyzedStack> stacks) {
        userStackRepository.deleteAllByUserId(user.getId());
        // 지우기 전에 새로 넣으면 유니크 제약에 걸린다.
        userStackRepository.flush();

        for (AnalysisAiResponse.AnalyzedStack stack : dedupeByName(stacks).values()) {
            String name = techStackExtractor.canonicalize(stack.name());
            if (name == null) {
                continue;
            }

            StackDetail detail = stackDetailRepository.findByStackName(name)
                    .orElseGet(() -> stackDetailRepository.save(StackDetail.ofName(name)));

            userStackRepository.save(UserStack.create(
                    user, detail, report, toStackLevel(stack.level()), stack.score(), stack.description()));
        }
    }

    /** 같은 기술을 두 번 내놓을 수 있다. 표준 이름이 겹치면 앞의 것만 남긴다. */
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
