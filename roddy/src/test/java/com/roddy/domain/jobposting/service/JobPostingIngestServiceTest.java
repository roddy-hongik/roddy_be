package com.roddy.domain.jobposting.service;

import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.enums.JobPostingStatus;
import com.roddy.domain.jobposting.dto.IngestSummary;
import com.roddy.domain.jobposting.entity.JobPosting;
import com.roddy.domain.jobposting.repository.JobPostingRepository;
import com.roddy.global.config.s3.S3Uploader;
import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.CrawlResult;
import com.roddy.global.crawler.CrawlSpecFixtures;
import com.roddy.global.crawler.spec.CrawlSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JobPostingIngestServiceTest {

    private static final LocalDateTime FIRST_RUN = LocalDateTime.of(2026, 3, 16, 4, 0);
    private static final LocalDateTime SECOND_RUN = FIRST_RUN.plusDays(1);

    @Autowired
    private JobPostingIngestService ingestService;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @MockitoBean
    private S3Uploader s3Uploader;

    @MockitoBean
    private SocialAuthService socialAuthService;

    private final CrawlSpec spec = CrawlSpecFixtures.spec("""
            company: kakao
            company_name_ko: 카카오
            source_type: json
            list: {url: https://example.com, response_path: jobList}
            required: [job_id, title]
            fields: {job_id: realId, title: jobOfferTitle}
            """);

    @BeforeEach
    void setUp() {
        jobPostingRepository.deleteAll();
    }

    @Test
    @DisplayName("처음 수집한 공고를 저장한다")
    void createsNewPostings() {
        IngestSummary summary = ingestService.ingest(spec, healthy(record("P-1", "백엔드"), record("P-2", "프론트")), FIRST_RUN);

        assertThat(summary.created()).isEqualTo(2);
        assertThat(summary.updated()).isZero();
        assertThat(summary.failed()).isZero();
        assertThat(jobPostingRepository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("같은 공고를 다시 수집해도 중복 저장하지 않는다")
    void upsertsInsteadOfDuplicating() {
        ingestService.ingest(spec, healthy(record("P-1", "백엔드")), FIRST_RUN);
        IngestSummary summary = ingestService.ingest(spec, healthy(record("P-1", "백엔드")), SECOND_RUN);

        assertThat(summary.created()).isZero();
        assertThat(summary.unchanged()).isEqualTo(1);
        assertThat(jobPostingRepository.findAll()).hasSize(1);
        assertThat(find("P-1").getCrawledAt()).isEqualTo(SECOND_RUN);
    }

    @Test
    @DisplayName("내용이 바뀐 공고는 갱신한다")
    void updatesChangedPosting() {
        ingestService.ingest(spec, healthy(record("P-1", "백엔드")), FIRST_RUN);
        IngestSummary summary = ingestService.ingest(spec, healthy(record("P-1", "백엔드 개발자 (수정)")), SECOND_RUN);

        assertThat(summary.updated()).isEqualTo(1);
        assertThat(summary.unchanged()).isZero();
        assertThat(find("P-1").getTitle()).isEqualTo("백엔드 개발자 (수정)");
    }

    @Test
    @DisplayName("목록에서 사라진 공고는 마감으로 처리한다")
    void closesDisappearedPosting() {
        ingestService.ingest(spec, healthy(record("P-1", "백엔드"), record("P-2", "프론트")), FIRST_RUN);
        IngestSummary summary = ingestService.ingest(spec, healthy(record("P-1", "백엔드")), SECOND_RUN);

        assertThat(summary.closed()).isEqualTo(1);
        assertThat(find("P-1").getStatus()).isEqualTo(JobPostingStatus.OPEN);
        assertThat(find("P-2").getStatus()).isEqualTo(JobPostingStatus.CLOSED);
        assertThat(find("P-2").getClosedAt()).isEqualTo(SECOND_RUN);
    }

    @Test
    @DisplayName("수집 점검에 걸린 회차에는 사라진 공고를 마감하지 않는다")
    void doesNotCloseWhenCrawlIsBroken() {
        ingestService.ingest(spec, healthy(record("P-1", "백엔드"), record("P-2", "프론트")), FIRST_RUN);

        // 사이트 개편으로 0건이 수집된 상황. 그대로 마감 처리하면 살아 있는 공고가 전부 사라진다.
        IngestSummary summary = ingestService.ingest(spec, broken(), SECOND_RUN);

        assertThat(summary.closed()).isZero();
        assertThat(find("P-1").getStatus()).isEqualTo(JobPostingStatus.OPEN);
        assertThat(find("P-2").getStatus()).isEqualTo(JobPostingStatus.OPEN);
    }

    @Test
    @DisplayName("마감일이 지난 공고는 점검에 걸린 회차에도 마감한다")
    void closesPassedDeadlineEvenWhenBroken() {
        CrawlRecord expiring = record("P-1", "백엔드", values -> values.put("deadline", "2026-03-15"));
        ingestService.ingest(spec, healthy(expiring), FIRST_RUN.minusDays(7));

        IngestSummary summary = ingestService.ingest(spec, broken(), SECOND_RUN);

        assertThat(summary.closed()).isEqualTo(1);
        assertThat(find("P-1").getStatus()).isEqualTo(JobPostingStatus.CLOSED);
    }

    @Test
    @DisplayName("적재할 수 없는 공고만 건너뛰고 나머지는 저장한다")
    void skipsOnlyUnusableRecords() {
        CrawlRecord withoutApplyUrl = new CrawlRecord(
                Map.of("job_id", "P-2", "title", "지원 URL 없는 공고"), Map.of());

        IngestSummary summary = ingestService.ingest(
                spec, healthy(record("P-1", "백엔드"), withoutApplyUrl), FIRST_RUN);

        assertThat(summary.created()).isEqualTo(1);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(jobPostingRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("수집 원본이 마감이라고 알려주면 그대로 마감 상태로 저장한다")
    void respectsClosedStateFromSource() {
        CrawlRecord closed = record("P-1", "백엔드", values -> values.put("is_closed", true));

        ingestService.ingest(spec, healthy(closed), FIRST_RUN);

        assertThat(find("P-1").getStatus()).isEqualTo(JobPostingStatus.CLOSED);
    }

    private JobPosting find(String externalId) {
        Optional<JobPosting> found = jobPostingRepository.findByCompanyCodeAndExternalId("kakao", externalId);
        assertThat(found).isPresent();
        return found.get();
    }

    private CrawlResult healthy(CrawlRecord... records) {
        return new CrawlResult("kakao", List.of(records), List.of());
    }

    private CrawlResult broken() {
        return new CrawlResult("kakao", List.of(), List.of("0건 수집 — url / 응답 경로 / 필터를 확인해야 합니다."));
    }

    private CrawlRecord record(String externalId, String title) {
        return record(externalId, title, values -> {
        });
    }

    private CrawlRecord record(String externalId, String title,
                               java.util.function.Consumer<Map<String, Object>> customizer) {
        Map<String, Object> values = new HashMap<>();
        values.put("job_id", externalId);
        values.put("title", title);
        values.put("apply_url", "https://careers.kakao.com/jobs/" + externalId);
        customizer.accept(values);
        return new CrawlRecord(values, Map.of());
    }
}
