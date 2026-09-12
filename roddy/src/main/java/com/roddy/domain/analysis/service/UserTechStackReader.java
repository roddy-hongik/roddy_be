package com.roddy.domain.analysis.service;

import com.roddy.domain.analysis.entity.UserStack;
import com.roddy.domain.analysis.repository.UserStackRepository;
import com.roddy.domain.jobposting.service.TechStackExtractor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
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

    private final UserStackRepository userStackRepository;
    private final TechStackExtractor techStackExtractor;

    /**
     * 기술 이름 → 숙련도 점수.
     *
     * <p>같은 기술이 여러 번 나오면 높은 점수를 남긴다. 아직 분석 결과가 없는 사용자는 빈 맵이다.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> read(Long userId) {
        if (userId == null) {
            return Map.of();
        }

        Map<String, Integer> scores = new HashMap<>();
        for (UserStack userStack : userStackRepository.findAllWithStackDetailByUserId(userId)) {
            String name = techStackExtractor.canonicalize(userStack.getStackDetail().getStackName());
            if (name == null) {
                continue;
            }
            scores.merge(name, Math.clamp(userStack.getScore(), MIN_SCORE, MAX_SCORE), Math::max);
        }
        return scores;
    }
}
