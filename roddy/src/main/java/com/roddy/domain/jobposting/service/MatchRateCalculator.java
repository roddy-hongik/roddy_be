package com.roddy.domain.jobposting.service;

import java.util.Collection;
import java.util.Map;

/**
 * 공고 하나의 매칭률.
 *
 * <p>목록과 상세가 같은 규칙을 써야 한다. 목록에서 본 숫자와 상세에서 본 숫자가 다르면 둘 다 믿을 수
 * 없게 되므로 계산을 한 자리에 둔다.
 */
public final class MatchRateCalculator {

    private static final int NO_SCORE = 0;

    private MatchRateCalculator() {
    }

    /**
     * 공고가 요구하는 기술마다 사용자의 숙련도 점수를 더해 평균 낸다. 갖고 있지 않은 기술은 0 점이므로
     * 요구 기술을 많이 가질수록, 그리고 깊이 알수록 높아진다.
     *
     * <p>견줄 것이 없으면 숫자를 만들지 않는다. 0 을 주면 "적합하지 않다"로 읽히는데, 실제로는
     * 아직 판단할 근거가 없다는 뜻이기 때문이다.
     */
    public static Integer calculate(Collection<String> requiredStacks, Map<String, Integer> userScores) {
        if (requiredStacks == null || requiredStacks.isEmpty() || userScores.isEmpty()) {
            return null;
        }

        int total = requiredStacks.stream()
                .mapToInt(stack -> userScores.getOrDefault(stack, NO_SCORE))
                .sum();
        return Math.round((float) total / requiredStacks.size());
    }
}
