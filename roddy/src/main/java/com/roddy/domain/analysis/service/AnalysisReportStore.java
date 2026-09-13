package com.roddy.domain.analysis.service;

import com.roddy.domain.analysis.dto.CompetencyCategory;
import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.analysis.entity.AnalysisReportCategory;
import com.roddy.domain.analysis.entity.StackDetail;
import com.roddy.domain.analysis.entity.UserStack;
import com.roddy.domain.analysis.enums.AnalysisStatus;
import com.roddy.domain.analysis.repository.AnalysisReportCategoryRepository;
import com.roddy.domain.analysis.repository.AnalysisReportRepository;
import com.roddy.domain.analysis.repository.StackDetailRepository;
import com.roddy.domain.analysis.repository.UserStackRepository;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.enums.DesiredJob;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 분석 리포트를 저장한다. 분석 실행과 저장을 나눠 두어야 실패해도 상태를 남길 수 있다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisReportStore {

    private final AnalysisReportRepository analysisReportRepository;
    private final AnalysisReportCategoryRepository analysisReportCategoryRepository;
    private final UserStackRepository userStackRepository;
    private final StackDetailRepository stackDetailRepository;
    private final UserRepository userRepository;
    private final TechStackExtractor techStackExtractor;
    private final CompetencyCategoryCatalog competencyCategoryCatalog;

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

        // 채점을 요청한 축만 받는다. 기준은 리포트에 남긴 직무의 축이다.
        List<CompetencyCategory> categories = competencyCategoryCatalog.categoriesOf(report.getDesiredJob());
        saveCategories(report, categories, response.categories());
        saveStacks(report, categories.stream().map(CompetencyCategory::code).collect(Collectors.toSet()),
                response.stacks());
    }

    @Transactional
    public void fail(Long reportId, String reason) {
        analysisReportRepository.findById(reportId).ifPresent(report -> report.fail(reason));
    }

    @Transactional(readOnly = true)
    public boolean isAnalyzing(Long userId) {
        return analysisReportRepository.existsByUserIdAndStatus(userId, AnalysisStatus.PENDING);
    }

    /** 분석을 요청한 당시의 직무. 이 리포트를 어떤 평가 축으로 채점할지 정하는 기준이다. */
    @Transactional(readOnly = true)
    public DesiredJob findDesiredJob(Long reportId) {
        return analysisReportRepository.findById(reportId)
                .map(AnalysisReport::getDesiredJob)
                .orElseThrow(() -> new IllegalStateException("분석 리포트가 없습니다. reportId=" + reportId));
    }

    /** 가장 최근에 요청한 분석. 진행 중이거나 실패한 것일 수 있다. */
    @Transactional(readOnly = true)
    public Optional<AnalysisReport> findLatest(Long userId) {
        return analysisReportRepository.findFirstByUserIdOrderByIdDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<AnalysisReportCategory> findCategories(Long reportId) {
        return analysisReportCategoryRepository.findAllByAnalysisReportIdOrderByIdAsc(reportId);
    }

    @Transactional(readOnly = true)
    public List<UserStack> findStacks(Long reportId) {
        return userStackRepository.findAllWithStackDetailByReportId(reportId);
    }

    /**
     * 축 정의의 순서대로 저장한다. 요청하지 않은 축은 버린다.
     *
     * <p>AI 가 빠뜨린 축은 0점으로 채우지 않는다. 0점은 "못한다"로 읽히는데 실제로는 판단하지 않은 것이다.
     */
    private void saveCategories(AnalysisReport report, List<CompetencyCategory> categories,
                                List<AnalysisAiResponse.CategoryScore> scores) {
        Map<String, AnalysisAiResponse.CategoryScore> scoreByCode = new HashMap<>();
        scores.forEach(score -> scoreByCode.putIfAbsent(score.code(), score));

        for (CompetencyCategory category : categories) {
            AnalysisAiResponse.CategoryScore score = scoreByCode.get(category.code());
            if (score == null) {
                log.warn("평가 축 점수가 빠졌습니다. reportId={} code={}", report.getId(), category.code());
                continue;
            }

            analysisReportCategoryRepository.save(AnalysisReportCategory.create(
                    report, category, score.score(), score.interpretation()));
        }
    }

    /**
     * 기술스택은 리포트에 딸려 저장한다. 지난 리포트의 기술은 지우지 않는다. 그 리포트를 다시 열었을 때
     * 그때 무엇을 할 줄 알았는지 보여야 하기 때문이다.
     *
     * @param categoryCodes 이 리포트의 평가 축. 여기 없는 축에는 기술을 매달지 않는다
     */
    private void saveStacks(AnalysisReport report, Set<String> categoryCodes,
                            List<AnalysisAiResponse.AnalyzedStack> stacks) {
        dedupeByName(stacks).forEach((name, stack) -> {
            StackDetail detail = stackDetailRepository.findByStackName(name)
                    .orElseGet(() -> stackDetailRepository.save(StackDetail.ofName(name)));
            String categoryCode = stack.category() != null && categoryCodes.contains(stack.category())
                    ? stack.category()
                    : null;

            userStackRepository.save(UserStack.create(
                    report.getUser(), detail, report, toStackLevel(stack.level()), stack.score(), stack.description(),
                    categoryCode, stack.foundInGithub(), stack.foundInPortfolio()));
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
