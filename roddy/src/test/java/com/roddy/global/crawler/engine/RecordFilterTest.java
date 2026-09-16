package com.roddy.global.crawler.engine;

import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.CrawlSpecFixtures;
import com.roddy.global.crawler.spec.CrawlSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RecordFilterTest {

    @Test
    @DisplayName("공고가 아닌 인재풀 항목을 대소문자 구분 없이 걸러낸다")
    void excludesTalentPool() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: musinsa
                source_type: embedded_json
                list: {url: https://example.com}
                fields: {job_id: openingId, title: title}
                filter:
                  field: title
                  exclude_contains:
                    - 인재풀
                    - Talent Pool
                """);

        List<CrawlRecord> filtered = RecordFilter.apply(List.of(
                record("1", "백엔드 개발자"),
                record("2", "개발 직군 인재풀"),
                record("3", "Backend talent pool"),
                record("4", "프론트엔드 개발자")
        ), spec.filter());

        assertThat(filtered).extracting(r -> r.text("job_id")).containsExactly("1", "4");
    }

    @Test
    @DisplayName("in 목록에 든 값만 남긴다")
    void keepsOnlyAllowedValues() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: example
                source_type: json
                list: {url: https://example.com}
                fields: {job_id: id, job_category: category}
                filter:
                  field: job_category
                  in: [개발, 데이터]
                """);

        List<CrawlRecord> filtered = RecordFilter.apply(List.of(
                categorized("1", "개발"),
                categorized("2", "디자인"),
                categorized("3", "데이터")
        ), spec.filter());

        assertThat(filtered).extracting(r -> r.text("job_id")).containsExactly("1", "3");
    }

    @Test
    @DisplayName("필터가 없으면 그대로 통과시킨다")
    void passesThroughWithoutFilter() {
        List<CrawlRecord> records = List.of(record("1", "백엔드 개발자"));

        assertThat(RecordFilter.apply(records, null)).isSameAs(records);
    }

    private CrawlRecord record(String jobId, String title) {
        return new CrawlRecord(Map.of("job_id", jobId, "title", title), Map.of());
    }

    private CrawlRecord categorized(String jobId, String category) {
        return new CrawlRecord(Map.of("job_id", jobId, "job_category", category), Map.of());
    }
}
