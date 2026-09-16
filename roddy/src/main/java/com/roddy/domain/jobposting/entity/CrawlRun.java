package com.roddy.domain.jobposting.entity;

import com.roddy.domain.BaseEntity;
import com.roddy.domain.jobposting.dto.IngestSummary;
import com.roddy.domain.jobposting.enums.CrawlRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 회사 한 곳의 수집 회차 기록.
 *
 * <p>사이트가 개편되면 명세가 조용히 어긋나 0건이 수집되거나 필수 필드가 통째로 빈다. 회차마다 결과를
 * 남겨 두어야 어느 회사가 언제부터 깨졌는지 추적할 수 있고, 어드민 수집 현황도 이 기록으로 그린다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "crawl_runs",
        indexes = {
                @Index(name = "idx_crawl_run_company_started", columnList = "company_code, started_at"),
                @Index(name = "idx_crawl_run_started_at", columnList = "started_at")
        }
)
public class CrawlRun extends BaseEntity {

    private static final int MESSAGE_MAX_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "crawl_run_id")
    private Long id;

    @Column(name = "company_code", nullable = false, length = 50)
    private String companyCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CrawlRunStatus status;

    @Column(nullable = false)
    private int collectedCount;

    @Column(nullable = false)
    private int createdCount;

    @Column(nullable = false)
    private int updatedCount;

    @Column(nullable = false)
    private int unchangedCount;

    @Column(nullable = false)
    private int closedCount;

    /** 필수 값이 없어 적재하지 못한 공고 수. */
    @Column(nullable = false)
    private int failedCount;

    /** 상세 페이지를 받아오지 못한 공고 수. 본문이 비어 있는 이유를 추적할 때 쓴다. */
    @Column(nullable = false)
    private int detailFailedCount;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private LocalDateTime finishedAt;

    /** 점검에서 걸린 문제나 예외 메시지. */
    @Column(length = MESSAGE_MAX_LENGTH)
    private String message;

    public static CrawlRun completed(String companyCode, IngestSummary summary, int detailFailedCount,
                                     List<String> issues, LocalDateTime startedAt, LocalDateTime finishedAt) {
        boolean clean = issues.isEmpty() && !summary.hasFailure() && detailFailedCount == 0;

        return CrawlRun.builder()
                .companyCode(companyCode)
                .status(clean ? CrawlRunStatus.SUCCESS : CrawlRunStatus.PARTIAL)
                .collectedCount(summary.collected())
                .createdCount(summary.created())
                .updatedCount(summary.updated())
                .unchangedCount(summary.unchanged())
                .closedCount(summary.closed())
                .failedCount(summary.failed())
                .detailFailedCount(detailFailedCount)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .message(truncate(issues.isEmpty() ? null : String.join(" / ", issues)))
                .build();
    }

    public static CrawlRun failed(String companyCode, String message,
                                  LocalDateTime startedAt, LocalDateTime finishedAt) {
        return CrawlRun.builder()
                .companyCode(companyCode)
                .status(CrawlRunStatus.FAILED)
                .collectedCount(0)
                .createdCount(0)
                .updatedCount(0)
                .unchangedCount(0)
                .closedCount(0)
                .failedCount(0)
                .detailFailedCount(0)
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .message(truncate(message))
                .build();
    }

    public boolean isSuccess() {
        return this.status == CrawlRunStatus.SUCCESS;
    }

    /** 예외 메시지는 길이를 알 수 없으므로 컬럼 크기에 맞춰 자른다. */
    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MESSAGE_MAX_LENGTH ? message : message.substring(0, MESSAGE_MAX_LENGTH);
    }
}
