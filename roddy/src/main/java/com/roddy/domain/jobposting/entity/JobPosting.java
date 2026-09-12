package com.roddy.domain.jobposting.entity;

import com.roddy.domain.BaseEntity;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.enums.JobPostingStatus;
import com.roddy.domain.enums.RecruitType;
import com.roddy.domain.jobposting.dto.JobPostingSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 회사 채용 사이트에서 수집한 채용공고.
 *
 * <p>{@code (companyCode, externalId)} 가 수집 원본을 가리키는 자연키이며, 이 조합으로 upsert 하여
 * 같은 공고가 매 수집마다 중복 적재되지 않도록 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "job_postings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_job_posting_source",
                        columnNames = {"company_code", "external_id"}
                )
        },
        indexes = {
                @Index(name = "idx_job_posting_company_code", columnList = "company_code"),
                @Index(name = "idx_job_posting_status_deadline", columnList = "status, deadline"),
                @Index(name = "idx_job_posting_desired_job", columnList = "desired_job")
        }
)
public class JobPosting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_posting_id")
    private Long id;

    /** 수집 명세의 company 값 (예: kakao). 회사 채용 사이트를 식별하는 코드. */
    @Column(name = "company_code", nullable = false, length = 50)
    private String companyCode;

    /** 수집 원본의 공고 고유 ID. companyCode 와 묶여 멱등 적재의 키가 된다. */
    @Column(name = "external_id", nullable = false, length = 200)
    private String externalId;

    /** 사용자에게 보여줄 회사명 (예: 카카오). */
    @Column(nullable = false)
    private String company;

    @Column(nullable = false, length = 500)
    private String title;

    /** 공고 본문. 목록 응답에 본문이 없는 회사는 상세 수집 전까지 비어 있을 수 있어 null 을 허용한다. */
    @Column(columnDefinition = "TEXT")
    private String content;

    /** 수집 원본의 직무 분류 문자열 (예: Server, 백엔드 개발). */
    @Column(length = 200)
    private String recruitField;

    /** recruitField 를 로디 직무 분류로 해석한 결과. 분류 배치 이전에는 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "desired_job", length = 50)
    private DesiredJob desiredJob;

    /** 인턴/신입/경력직. 경력 정보를 주지 않는 회사가 많아 null 을 허용한다. */
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private RecruitType recruitType;

    @Column(length = 200)
    private String location;

    /** 정규직/계약직 등 고용 형태 원본 문자열. */
    @Column(length = 100)
    private String employmentType;

    @Column(nullable = false, length = 1000)
    private String applyUrl;

    private LocalDateTime postedAt;

    /** 마감일. 상시 채용이면 null. */
    private LocalDateTime deadline;

    /** 수집 원본이 알려준 최종 수정 시각. 로디 DB 기준인 {@code BaseEntity.updatedAt} 과 구분된다. */
    private LocalDateTime sourceUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobPostingStatus status;

    /** 마감을 감지한 시각. */
    private LocalDateTime closedAt;

    /** 본문 변경 감지용 해시. 이전 수집과 같으면 갱신을 건너뛴다. */
    @Column(length = 64)
    private String contentHash;

    /** 수집 원본 레코드 원문. 명세가 바뀌었을 때 재파싱하기 위해 보관한다. */
    @Column(columnDefinition = "LONGTEXT")
    private String rawJson;

    @Column(nullable = false)
    private LocalDateTime crawledAt;

    public static JobPosting create(JobPostingSnapshot snapshot, LocalDateTime crawledAt) {
        boolean closed = snapshot.isClosedBySource();

        return JobPosting.builder()
                .companyCode(snapshot.companyCode())
                .externalId(snapshot.externalId())
                .company(snapshot.company())
                .title(snapshot.title())
                .content(snapshot.content())
                .recruitField(snapshot.recruitField())
                .desiredJob(snapshot.desiredJob())
                .recruitType(snapshot.recruitType())
                .location(snapshot.location())
                .employmentType(snapshot.employmentType())
                .applyUrl(snapshot.applyUrl())
                .postedAt(snapshot.postedAt())
                .deadline(snapshot.deadline())
                .sourceUpdatedAt(snapshot.sourceUpdatedAt())
                .status(closed ? JobPostingStatus.CLOSED : JobPostingStatus.OPEN)
                .closedAt(closed ? crawledAt : null)
                .contentHash(snapshot.contentHash())
                .rawJson(snapshot.rawJson())
                .crawledAt(crawledAt)
                .build();
    }

    /**
     * 재수집한 스냅샷으로 공고를 갱신한다.
     *
     * <p>수집 원본이 마감 여부를 알려준 경우에만 상태를 바꾼다. 대부분의 회사는 마감 필드를 주지 않고
     * 목록에서 공고가 사라지는 것으로 마감을 알리므로, 그 판정은 수집 서비스가 {@link #close}로 처리한다.
     */
    public void update(JobPostingSnapshot snapshot, LocalDateTime crawledAt) {
        this.company = snapshot.company();
        this.title = snapshot.title();
        this.content = snapshot.content();
        this.recruitField = snapshot.recruitField();
        this.desiredJob = snapshot.desiredJob();
        this.recruitType = snapshot.recruitType();
        this.location = snapshot.location();
        this.employmentType = snapshot.employmentType();
        this.applyUrl = snapshot.applyUrl();
        this.postedAt = snapshot.postedAt();
        this.deadline = snapshot.deadline();
        this.sourceUpdatedAt = snapshot.sourceUpdatedAt();
        this.contentHash = snapshot.contentHash();
        this.rawJson = snapshot.rawJson();
        this.crawledAt = crawledAt;

        if (snapshot.isClosedBySource()) {
            close(crawledAt);
        } else if (snapshot.isOpenBySource()) {
            reopen();
        }
    }

    /** 수집 시각만 갱신한다. 내용이 그대로일 때 "아직 살아있는 공고"임을 기록하는 용도. */
    public void touch(LocalDateTime crawledAt) {
        this.crawledAt = crawledAt;
    }

    public void close(LocalDateTime closedAt) {
        this.status = JobPostingStatus.CLOSED;
        this.closedAt = closedAt;
    }

    public void reopen() {
        this.status = JobPostingStatus.OPEN;
        this.closedAt = null;
    }

    /** 상세 재수집 여부 판단용. 해시가 같으면 목록 기준으로 바뀐 게 없다는 뜻이다. */
    public boolean hasSameContent(JobPostingSnapshot snapshot) {
        return this.contentHash != null && this.contentHash.equals(snapshot.contentHash());
    }

    public boolean isDeadlinePassed(LocalDateTime now) {
        return this.deadline != null && this.deadline.isBefore(now);
    }
}
