package com.roddy.domain.jobposting.entity;

import com.roddy.domain.jobposting.dto.IngestSummary;
import com.roddy.domain.jobposting.enums.CrawlRunStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlRunTest {

    private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, 3, 16, 4, 0);
    private static final LocalDateTime FINISHED_AT = STARTED_AT.plusMinutes(2);

    @Test
    @DisplayName("점검을 통과하고 실패가 없으면 성공으로 남긴다")
    void recordsSuccess() {
        CrawlRun run = CrawlRun.completed("kakao", summary(0), 0, List.of(), STARTED_AT, FINISHED_AT);

        assertThat(run.getStatus()).isEqualTo(CrawlRunStatus.SUCCESS);
        assertThat(run.isSuccess()).isTrue();
        assertThat(run.getMessage()).isNull();
    }

    @Test
    @DisplayName("점검에 걸리면 일부 실패로 남기고 이유를 적는다")
    void recordsPartialWhenCheckFails() {
        CrawlRun run = CrawlRun.completed("kakao", summary(0), 0,
                List.of("필수 필드 'title' 가 비어 있습니다: 3/50"), STARTED_AT, FINISHED_AT);

        assertThat(run.getStatus()).isEqualTo(CrawlRunStatus.PARTIAL);
        assertThat(run.getMessage()).contains("title");
    }

    @Test
    @DisplayName("적재나 상세 수집이 일부 실패해도 일부 실패로 남긴다")
    void recordsPartialWhenSomeRecordsFail() {
        CrawlRun ingestFailed = CrawlRun.completed("kakao", summary(2), 0, List.of(), STARTED_AT, FINISHED_AT);
        CrawlRun detailFailed = CrawlRun.completed("kakao", summary(0), 3, List.of(), STARTED_AT, FINISHED_AT);

        assertThat(ingestFailed.getStatus()).isEqualTo(CrawlRunStatus.PARTIAL);
        assertThat(detailFailed.getStatus()).isEqualTo(CrawlRunStatus.PARTIAL);
    }

    @Test
    @DisplayName("수집 자체가 실패하면 실패로 남긴다")
    void recordsFailure() {
        CrawlRun run = CrawlRun.failed("kakao", "RestClientException: 503", STARTED_AT, FINISHED_AT);

        assertThat(run.getStatus()).isEqualTo(CrawlRunStatus.FAILED);
        assertThat(run.getCollectedCount()).isZero();
        assertThat(run.getMessage()).contains("503");
    }

    @Test
    @DisplayName("긴 예외 메시지는 컬럼 크기에 맞춰 자른다")
    void truncatesLongMessage() {
        CrawlRun run = CrawlRun.failed("kakao", "x".repeat(5000), STARTED_AT, FINISHED_AT);

        assertThat(run.getMessage()).hasSize(2000);
    }

    private IngestSummary summary(int failed) {
        return new IngestSummary(50, 5, 3, 42, 1, failed);
    }
}
