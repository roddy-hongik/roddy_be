package com.roddy.domain.graph.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 같은 공고에서 함께 요구되는 기술 쌍.
 *
 * <p>신뢰도는 둘 중 덜 요구되는 기술을 기준으로, 그 기술이 나온 공고 가운데 다른 기술도 함께 나온 비율이다.
 * QueryDSL 처럼 드물게 나오는 기술이 늘 Spring 과 함께 나오면 1.0 에 가깝다.
 *
 * @param source 이름순으로 앞선 기술
 * @param target 이름순으로 뒤선 기술
 * @param count  두 기술이 함께 나온 공고 수
 */
public record TechCoOccurrence(String source, String target, int count, double confidence) {

    /** 함께 나온 공고가 이보다 적으면 우연일 수 있어 관계로 보지 않는다. */
    static final int MIN_COUNT = 3;
    /** 신뢰도가 이보다 낮으면 관계로 보지 않는다. */
    static final double MIN_CONFIDENCE = 0.3;

    /** @param stacksByPosting 공고마다 요구하는 기술 */
    public static List<TechCoOccurrence> calculate(Collection<? extends Set<String>> stacksByPosting) {
        Map<String, Integer> stackCounts = new HashMap<>();
        Map<List<String>, Integer> pairCounts = new HashMap<>();

        for (Set<String> stacks : stacksByPosting) {
            List<String> sorted = stacks.stream().sorted().toList();
            sorted.forEach(stack -> stackCounts.merge(stack, 1, Integer::sum));
            for (int i = 0; i < sorted.size(); i++) {
                for (int j = i + 1; j < sorted.size(); j++) {
                    pairCounts.merge(List.of(sorted.get(i), sorted.get(j)), 1, Integer::sum);
                }
            }
        }

        return pairCounts.entrySet().stream()
                .filter(pair -> pair.getValue() >= MIN_COUNT)
                .map(pair -> of(pair.getKey().get(0), pair.getKey().get(1), pair.getValue(), stackCounts))
                .filter(pair -> pair.confidence() >= MIN_CONFIDENCE)
                .sorted(Comparator.comparing(TechCoOccurrence::source).thenComparing(TechCoOccurrence::target))
                .toList();
    }

    private static TechCoOccurrence of(String source, String target, int count, Map<String, Integer> stackCounts) {
        int rarer = Math.min(stackCounts.get(source), stackCounts.get(target));
        return new TechCoOccurrence(source, target, count, Math.round((double) count / rarer * 100) / 100.0);
    }
}
