package com.roddy.domain.graph.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TechCoOccurrenceTest {

    @Test
    void 덜_요구되는_기술을_기준으로_함께_나온_비율을_신뢰도로_삼는다() {
        List<Set<String>> postings = new ArrayList<>();
        postings.add(Set.of("Java", "Spring", "QueryDSL"));
        postings.add(Set.of("Java", "Spring", "QueryDSL"));
        postings.add(Set.of("Java", "Spring", "QueryDSL"));
        postings.add(Set.of("Java", "Spring"));
        postings.add(Set.of("Java"));

        List<TechCoOccurrence> pairs = TechCoOccurrence.calculate(postings);

        assertThat(pairs).containsExactly(
                new TechCoOccurrence("Java", "QueryDSL", 3, 1.0),
                new TechCoOccurrence("Java", "Spring", 4, 1.0),
                new TechCoOccurrence("QueryDSL", "Spring", 3, 1.0));
    }

    @Test
    void 함께_나온_공고가_적거나_비율이_낮으면_관계로_보지_않는다() {
        List<Set<String>> postings = new ArrayList<>();
        // Redis 는 5번, Java 와는 3번 함께 → 0.6
        for (int i = 0; i < 3; i++) {
            postings.add(Set.of("Java", "Redis"));
        }
        postings.add(Set.of("Redis"));
        postings.add(Set.of("Redis"));
        // Git 과 Java 는 2번만 함께
        postings.add(Set.of("Java", "Git"));
        postings.add(Set.of("Java", "Git"));
        // Docker 와 Linux 는 각각 20번, 함께는 4번 → 0.2
        for (int i = 0; i < 4; i++) {
            postings.add(Set.of("Docker", "Linux"));
        }
        for (int i = 0; i < 16; i++) {
            postings.add(Set.of("Docker"));
            postings.add(Set.of("Linux"));
        }

        List<TechCoOccurrence> pairs = TechCoOccurrence.calculate(postings);

        assertThat(pairs).containsExactly(new TechCoOccurrence("Java", "Redis", 3, 0.6));
    }

    @Test
    void 공고가_없으면_관계도_없다() {
        assertThat(TechCoOccurrence.calculate(List.of())).isEmpty();
    }
}
