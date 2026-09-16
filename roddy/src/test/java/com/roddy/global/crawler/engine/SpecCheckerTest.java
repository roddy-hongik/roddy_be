package com.roddy.global.crawler.engine;

import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.CrawlSpecFixtures;
import com.roddy.global.crawler.spec.CrawlSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpecCheckerTest {

    private final CrawlSpec spec = CrawlSpecFixtures.spec("""
            company: kakao
            source_type: json
            list: {url: https://example.com, response_path: jobList}
            required: [job_id, title]
            fields: {job_id: realId, title: jobOfferTitle}
            """);

    @Test
    @DisplayName("정상 수집이면 문제를 보고하지 않는다")
    void reportsNothingForHealthyResult() {
        List<String> issues = SpecChecker.check(List.of(record("1", "백엔드"), record("2", "프론트")), spec);

        assertThat(issues).isEmpty();
    }

    @Test
    @DisplayName("0건이면 명세가 어긋났을 수 있다고 알린다")
    void reportsEmptyResult() {
        List<String> issues = SpecChecker.check(List.of(), spec);

        assertThat(issues).singleElement().asString().contains("0건 수집");
    }

    @Test
    @DisplayName("필수 필드가 빈 레코드 수를 센다")
    void reportsMissingRequiredField() {
        List<String> issues = SpecChecker.check(List.of(record("1", "백엔드"), record("2", null)), spec);

        assertThat(issues).singleElement().asString().contains("'title'").contains("1/2");
    }

    @Test
    @DisplayName("job_id 가 겹치면 알린다")
    void reportsDuplicateJobId() {
        List<String> issues = SpecChecker.check(List.of(record("1", "백엔드"), record("1", "프론트")), spec);

        assertThat(issues).singleElement().asString().contains("job_id 중복");
    }

    private CrawlRecord record(String jobId, String title) {
        Map<String, Object> values = new HashMap<>();
        values.put("job_id", jobId);
        values.put("title", title);
        return new CrawlRecord(values, Map.of());
    }
}
