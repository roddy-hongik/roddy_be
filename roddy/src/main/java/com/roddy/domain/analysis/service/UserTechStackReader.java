package com.roddy.domain.analysis.service;

import com.roddy.domain.analysis.entity.UserStack;
import com.roddy.domain.analysis.enums.AnalysisStatus;
import com.roddy.domain.analysis.repository.AnalysisReportRepository;
import com.roddy.domain.analysis.repository.UserStackRepository;
import com.roddy.domain.jobposting.service.TechStackExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 분석 리포트가 남긴 사용자 기술스택을 읽는다.
 *
 * <p>이름을 기술스택 사전의 표준 이름으로 맞춰서 돌려준다. 공고에서 뽑은 기술과 같은 사전을 거쳐야
 * 서로 이어지기 때문이다. 사용자 기술이 "자바"로 적혀 있어도 공고의 "Java"와 맞는다.
 */
@Component
@RequiredArgsConstructor
public class UserTechStackReader {

    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 100;

    private final AnalysisReportRepository analysisReportRepository;
    private final UserStackRepository userStackRepository;
    private final TechStackExtractor techStackExtractor;

    /**
     * 기술 이름 → 숙련도 점수.
     *
     * <p>가장 최근에 끝난 리포트의 기술만 본다. 지난 리포트의 기술까지 섞으면 지금 실력과 어긋나고,
     * 분석 중인 리포트는 아직 기술이 비어 있다. 아직 분석 결과가 없는 사용자는 빈 맵이다.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> read(Long userId) {
        if (userId == null) {
            return Map.of();
        }

        return analysisReportRepository.findFirstByUserIdAndStatusOrderByIdDesc(userId, AnalysisStatus.COMPLETED)
                .map(report -> toScores(userStackRepository.findAllWithStackDetailByReportId(report.getId())))
                .orElseGet(Map::of);
    }

    /** 표준 이름으로 맞추면 같은 기술이 여러 번 나올 수 있다. 그때는 높은 점수를 남긴다. */
    private Map<String, Integer> toScores(List<UserStack> userStacks) {
        Map<String, Integer> scores = new HashMap<>();
        for (UserStack userStack : userStacks) {
            String name = techStackExtractor.canonicalize(userStack.getStackDetail().getStackName());
            if (name == null) {
                continue;
            }
            scores.merge(name, Math.clamp(userStack.getScore(), MIN_SCORE, MAX_SCORE), Math::max);
        }
        return scores;
    }
}
