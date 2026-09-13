package com.roddy.domain.jobposting.service;

import com.roddy.domain.enums.JobPostingStatus;
import com.roddy.domain.jobposting.dto.IngestSummary;
import com.roddy.domain.jobposting.dto.JobPostingSnapshot;
import com.roddy.domain.jobposting.entity.JobPosting;
import com.roddy.domain.jobposting.repository.JobPostingRepository;
import com.roddy.domain.notification.NotificationService;
import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.CrawlResult;
import com.roddy.global.crawler.spec.CrawlSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 수집 결과를 채용공고 테이블에 반영한다.
 *
 * <p>{@code (companyCode, externalId)} 로 upsert 하므로 같은 회차를 여러 번 돌려도 중복되지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobPostingIngestService {

    private final JobPostingRepository jobPostingRepository;
    private final JobPostingSnapshotConverter converter;
    private final TechStackExtractor techStackExtractor;
    private final NotificationService notificationService;

    @Transactional
    public IngestSummary ingest(CrawlSpec spec, CrawlResult result, LocalDateTime crawledAt) {
        Set<String> collectedIds = new HashSet<>();
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int failed = 0;

        for (CrawlRecord record : result.records()) {
            try {
                JobPostingSnapshot snapshot = converter.convert(spec, record);
                collectedIds.add(snapshot.externalId());

                Optional<JobPosting> existing = jobPostingRepository
                        .findByCompanyCodeAndExternalId(snapshot.companyCode(), snapshot.externalId());
                boolean newlyCreated = existing.isEmpty();
                JobPosting posting;
                if (newlyCreated) {
                    posting = jobPostingRepository.save(JobPosting.create(snapshot, crawledAt));
                    created++;
                } else if (existing.get().hasSameContent(snapshot)) {
                    posting = existing.get();
                    posting.touch(crawledAt);
                    unchanged++;
                } else {
                    posting = existing.get();
                    posting.update(snapshot, crawledAt);
                    updated++;
                }

                // 내용이 그대로여도 다시 뽑는다. 사전이 늘어나면 기존 공고도 따라 채워진다.
                posting.updateTechStacks(techStackExtractor.extract(snapshot));
                if (newlyCreated && posting.getStatus() == JobPostingStatus.OPEN) {
                    try {
                        notificationService.createJobMatchNotifications(posting);
                    } catch (RuntimeException notificationError) {
                        log.warn("[{}] 맞춤 공고 알림을 만들지 못했습니다: {}",
                                spec.company(), notificationError.getMessage());
                    }
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("[{}] 공고를 적재하지 못했습니다: {}", spec.company(), e.getMessage());
            }
        }

        int closed = close(spec.company(), collectedIds, result, crawledAt);
        return new IngestSummary(result.size(), created, updated, unchanged, closed, failed);
    }

    /**
     * 마감을 판정한다.
     *
     * <p>마감일이 지난 공고는 언제나 닫는다. 목록에서 사라진 공고를 닫는 판정은 <b>점검을 통과한
     * 회차에만</b> 한다. 사이트가 개편돼 0건이 수집된 회차에 사라짐 판정을 그대로 적용하면 살아 있는
     * 공고를 전부 마감시키게 되고, 원래 상태로 되돌릴 방법이 없다.
     */
    private int close(String companyCode, Set<String> collectedIds, CrawlResult result, LocalDateTime crawledAt) {
        List<JobPosting> openPostings =
                jobPostingRepository.findAllByCompanyCodeAndStatus(companyCode, JobPostingStatus.OPEN);
        boolean canCloseDisappeared = result.isHealthy();
        int closed = 0;

        for (JobPosting posting : openPostings) {
            boolean deadlinePassed = posting.isDeadlinePassed(crawledAt);
            boolean disappeared = canCloseDisappeared && !collectedIds.contains(posting.getExternalId());
            if (deadlinePassed || disappeared) {
                posting.close(crawledAt);
                closed++;
            }
        }

        if (!canCloseDisappeared) {
            log.warn("[{}] 수집 점검에 걸려 사라진 공고 마감 처리를 건너뜁니다: {}", companyCode, result.issues());
        }
        return closed;
    }
}
