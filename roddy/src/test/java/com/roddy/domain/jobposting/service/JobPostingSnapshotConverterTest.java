package com.roddy.domain.jobposting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roddy.domain.enums.RecruitType;
import com.roddy.domain.jobposting.dto.JobPostingSnapshot;
import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.CrawlSpecFixtures;
import com.roddy.global.crawler.spec.CrawlSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobPostingSnapshotConverterTest {

    private final JobPostingSnapshotConverter converter = new JobPostingSnapshotConverter(new ObjectMapper());

    private final CrawlSpec spec = CrawlSpecFixtures.spec("""
            company: kakao
            company_name_ko: 카카오
            source_type: json
            list: {url: https://example.com, response_path: jobList}
            fields: {job_id: realId, title: jobOfferTitle}
            """);

    @Test
    @DisplayName("수집 레코드를 도메인 스냅샷으로 옮긴다")
    void convertsRecord() {
        JobPostingSnapshot snapshot = converter.convert(spec, record(values -> {
            values.put("job_id", "P-1");
            values.put("title", "백엔드 개발자");
            values.put("description", "서버 개발");
            values.put("job_category", "Server");
            values.put("location", "판교");
            values.put("employee_type", "정규직");
            values.put("apply_url", "https://careers.kakao.com/jobs/P-1");
            values.put("posted_at", "2026.05.14 00:00:00");
            values.put("deadline", "2026-06-01");
            values.put("updated_at", "2026-05-20T10:00:00");
        }));

        assertThat(snapshot.companyCode()).isEqualTo("kakao");
        assertThat(snapshot.company()).isEqualTo("카카오");
        assertThat(snapshot.externalId()).isEqualTo("P-1");
        assertThat(snapshot.title()).isEqualTo("백엔드 개발자");
        assertThat(snapshot.content()).isEqualTo("서버 개발");
        assertThat(snapshot.recruitField()).isEqualTo("Server");
        assertThat(snapshot.location()).isEqualTo("판교");
        assertThat(snapshot.employmentType()).isEqualTo("정규직");
        assertThat(snapshot.postedAt()).isEqualTo(LocalDateTime.of(2026, 5, 14, 0, 0));
        assertThat(snapshot.deadline()).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0));
        assertThat(snapshot.sourceUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 20, 10, 0));
        assertThat(snapshot.rawJson()).contains("P-1");
    }

    @Test
    @DisplayName("표시용 회사명이 없으면 회사 코드를 쓴다")
    void fallsBackToCompanyCode() {
        CrawlSpec withoutName = CrawlSpecFixtures.spec("""
                company: bucketplace
                source_type: json
                list: {url: https://example.com, response_path: jobs}
                fields: {job_id: id, title: title}
                """);

        JobPostingSnapshot snapshot = converter.convert(withoutName, minimalRecord());

        assertThat(snapshot.company()).isEqualTo("bucketplace");
    }

    @Test
    @DisplayName("고용 형태 필드명이 명세마다 달라도 읽는다")
    void readsEitherEmploymentTypeField() {
        JobPostingSnapshot fromEmployee = converter.convert(spec, record(values -> {
            values.putAll(minimalValues());
            values.put("employee_type", "계약직");
        }));
        JobPostingSnapshot fromEmployment = converter.convert(spec, record(values -> {
            values.putAll(minimalValues());
            values.put("employment_type", "정규직");
        }));

        assertThat(fromEmployee.employmentType()).isEqualTo("계약직");
        assertThat(fromEmployment.employmentType()).isEqualTo("정규직");
    }

    @ParameterizedTest(name = "career={0} → {1}")
    @CsvSource({
            "인턴,       INTERN",
            "체험형 인턴, INTERN",
            "신입,       JUNIOR",
            "경력,       SENIOR",
            "경력 3년 이상, SENIOR"
    })
    @DisplayName("경력 표기를 채용 구분으로 옮긴다")
    void mapsCareerToRecruitType(String career, RecruitType expected) {
        JobPostingSnapshot snapshot = converter.convert(spec, record(values -> {
            values.putAll(minimalValues());
            values.put("career", career);
        }));

        assertThat(snapshot.recruitType()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"신입/경력", "무관", "경력무관"})
    @NullSource
    @DisplayName("구분이 없거나 둘 다 걸리면 채용 구분을 비워 둔다")
    void leavesRecruitTypeNullWhenAmbiguous(String career) {
        JobPostingSnapshot snapshot = converter.convert(spec, record(values -> {
            values.putAll(minimalValues());
            values.put("career", career);
        }));

        assertThat(snapshot.recruitType()).isNull();
    }

    @ParameterizedTest(name = "is_closed={0} → {1}")
    @CsvSource({
            "in_progress, false",
            "open,        false",
            "모집중,       false",
            "closed,      true",
            "마감,         true",
            "채용 종료,     true"
    })
    @DisplayName("회사마다 다른 마감 표현을 읽는다")
    void readsClosedState(String raw, boolean expected) {
        JobPostingSnapshot snapshot = converter.convert(spec, record(values -> {
            values.putAll(minimalValues());
            values.put("is_closed", raw);
        }));

        assertThat(snapshot.closed()).isEqualTo(expected);
    }

    @Test
    @DisplayName("불리언으로 오는 마감 여부도 읽고, 알 수 없으면 비워 둔다")
    void readsBooleanAndUnknownClosedState() {
        JobPostingSnapshot closed = converter.convert(spec, record(values -> {
            values.putAll(minimalValues());
            values.put("is_closed", true);
        }));
        JobPostingSnapshot unknownValue = converter.convert(spec, record(values -> {
            values.putAll(minimalValues());
            values.put("is_closed", "알 수 없는 값");
        }));
        JobPostingSnapshot notProvided = converter.convert(spec, minimalRecord());

        assertThat(closed.closed()).isTrue();
        assertThat(unknownValue.closed()).isNull();
        assertThat(notProvided.closed()).isNull();
    }

    @Test
    @DisplayName("마감일이 게시일보다 빠르면 공고를 버리지 않고 마감일만 비운다")
    void dropsDeadlineEarlierThanPostedAt() {
        JobPostingSnapshot snapshot = converter.convert(spec, record(values -> {
            values.putAll(minimalValues());
            values.put("posted_at", "2026-05-14");
            values.put("deadline", "2021-12-31");
        }));

        assertThat(snapshot.postedAt()).isEqualTo(LocalDateTime.of(2026, 5, 14, 0, 0));
        assertThat(snapshot.deadline()).isNull();
    }

    @Test
    @DisplayName("지원 URL 이 없으면 명세가 덜 채워졌다고 알린다")
    void rejectsRecordWithoutApplyUrl() {
        assertThatThrownBy(() -> converter.convert(spec, record(values -> {
            values.put("job_id", "P-1");
            values.put("title", "백엔드 개발자");
        })))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apply_url");
    }

    @Test
    @DisplayName("상세 수집 실패 이유도 원본에 남긴다")
    void keepsDetailErrorInRawJson() {
        CrawlRecord failed = new CrawlRecord(minimalValues(), Map.of(), "HttpServerErrorException: 500");

        assertThat(converter.convert(spec, failed).rawJson()).contains("detail_error");
    }

    private CrawlRecord minimalRecord() {
        return new CrawlRecord(minimalValues(), Map.of());
    }

    private Map<String, Object> minimalValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("job_id", "P-1");
        values.put("title", "백엔드 개발자");
        values.put("apply_url", "https://careers.kakao.com/jobs/P-1");
        return values;
    }

    private CrawlRecord record(java.util.function.Consumer<Map<String, Object>> customizer) {
        Map<String, Object> values = new HashMap<>();
        customizer.accept(values);
        return new CrawlRecord(values, Map.of());
    }
}
