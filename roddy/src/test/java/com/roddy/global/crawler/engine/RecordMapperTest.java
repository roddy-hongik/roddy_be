package com.roddy.global.crawler.engine;

import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.CrawlSpecFixtures;
import com.roddy.global.crawler.spec.CrawlSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecordMapperTest {

    @Test
    @DisplayName("경로 매핑 / 배열 필드 / 지원 URL 조립이 파이썬 엔진과 같은 결과를 낸다")
    void mapsRecordLikeReferenceEngine() {
        // collector/run.py --selftest 와 같은 입력. 두 구현이 같은 결과를 내는지 보는 계약 테스트다.
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: kakao
                source_type: json
                list:
                  url: https://careers.kakao.com/public/api/job-list
                  response_path: jobList
                fields:
                  job_id: realId
                  title: jobOfferTitle
                  updated_at: uptDate
                  description: workContentDesc
                  is_closed: closeFlag
                  location: locationName
                apply_url:
                  template: "https://careers.kakao.com/jobs/{job_id}"
                extra:
                  skill_sets: skillSetList[].skillSetName
                """);

        CrawlRecord record = RecordMapper.map(CrawlSpecFixtures.json("""
                {
                  "realId": "P-1",
                  "jobOfferTitle": "백엔드 개발자",
                  "uptDate": "2026-05-01",
                  "workContentDesc": "서버 개발",
                  "qualification": "Java",
                  "closeFlag": false,
                  "locationName": "판교",
                  "skillSetList": [{"skillSetName": "Server"}, {"skillSetName": "Kotlin"}]
                }
                """), spec);

        assertThat(record.text("job_id")).isEqualTo("P-1");
        assertThat(record.text("title")).isEqualTo("백엔드 개발자");
        assertThat(record.text("location")).isEqualTo("판교");
        assertThat(record.value("is_closed")).isEqualTo(false);
        assertThat(record.text(CrawlRecord.APPLY_URL)).isEqualTo("https://careers.kakao.com/jobs/P-1");
        assertThat(record.extra().get("skill_sets")).isEqualTo(List.of("Server", "Kotlin"));
    }

    @Test
    @DisplayName("본문이 여러 조각으로 나뉜 회사는 빈 조각을 빼고 이어붙인다")
    void joinsMultiPathField() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: kakao
                source_type: json
                list: {url: https://example.com, response_path: jobList}
                fields:
                  description:
                    - introduction
                    - workContentDesc
                    - qualification
                """);

        CrawlRecord record = RecordMapper.map(CrawlSpecFixtures.json("""
                {"introduction": "팀 소개", "workContentDesc": "", "qualification": "Java"}
                """), spec);

        assertThat(record.text("description")).isEqualTo("팀 소개\n\nJava");
    }

    @Test
    @DisplayName("해석되지 않은 필드는 null 로 남는다")
    void leavesUnresolvedFieldNull() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: kakao
                source_type: json
                list: {url: https://example.com, response_path: jobList}
                fields:
                  job_id: id
                  deadline: closedAt
                """);

        CrawlRecord record = RecordMapper.map(CrawlSpecFixtures.json("""
                {"id": "1"}
                """), spec);

        assertThat(record.values()).containsKey("deadline");
        assertThat(record.value("deadline")).isNull();
        assertThat(record.isBlank("deadline")).isTrue();
    }

    @Test
    @DisplayName("null_values 규칙에 걸리는 값은 비운다")
    void appliesNullValues() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: nhn
                source_type: json
                list: {url: https://example.com, response_path: result}
                fields:
                  job_id: id
                  deadline: closeDate
                null_values:
                  deadline: "2999"
                """);

        CrawlRecord alwaysOpen = RecordMapper.map(CrawlSpecFixtures.json("""
                {"id": "1", "closeDate": "2999-12-31"}
                """), spec);
        CrawlRecord withDeadline = RecordMapper.map(CrawlSpecFixtures.json("""
                {"id": "2", "closeDate": "2026-03-31"}
                """), spec);

        assertThat(alwaysOpen.value("deadline")).isNull();
        assertThat(withDeadline.text("deadline")).isEqualTo("2026-03-31");
    }

    @Test
    @DisplayName("metadata 배열에서 이름으로 찾은 값이 기본 매핑을 덮어쓴다")
    void appliesMetadataExtraction() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: toss
                source_type: json
                list: {url: https://example.com, response_path: success}
                fields:
                  job_id: id
                  deadline: application_deadline
                metadata_extraction:
                  source_field: metadata
                  match_by: name
                  value_from: value
                  mappings:
                    deadline: "채용 마감일"
                    employment_type: "Employment_Type"
                """);

        CrawlRecord record = RecordMapper.map(CrawlSpecFixtures.json("""
                {
                  "id": "4321",
                  "application_deadline": null,
                  "metadata": [
                    {"name": "채용 마감일", "value": "2026-04-30"},
                    {"name": "Employment_Type", "value": "정규직"},
                    {"name": "쓰지 않는 필드", "value": "무시"}
                  ]
                }
                """), spec);

        assertThat(record.text("deadline")).isEqualTo("2026-04-30");
        assertThat(record.text("employment_type")).isEqualTo("정규직");
    }

    @Test
    @DisplayName("템플릿에 채울 값이 없으면 지원 URL 을 만들지 않는다")
    void skipsApplyUrlWhenPlaceholderMissing() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: kakao
                source_type: json
                list: {url: https://example.com, response_path: jobList}
                fields:
                  title: jobOfferTitle
                apply_url:
                  template: "https://careers.kakao.com/jobs/{job_id}"
                """);

        CrawlRecord record = RecordMapper.map(CrawlSpecFixtures.json("""
                {"jobOfferTitle": "백엔드 개발자"}
                """), spec);

        assertThat(record.value(CrawlRecord.APPLY_URL)).isNull();
    }
}
