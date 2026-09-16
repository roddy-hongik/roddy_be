package com.roddy.domain.jobposting.service;

import com.roddy.domain.graph.service.TechGraphService;
import com.roddy.domain.jobposting.dto.IngestSummary;
import com.roddy.domain.jobposting.entity.CrawlRun;
import com.roddy.domain.jobposting.repository.CrawlRunRepository;
import com.roddy.global.crawler.CrawlResult;
import com.roddy.global.crawler.engine.DeclarativeCrawler;
import com.roddy.global.crawler.spec.CrawlSpec;
import com.roddy.global.crawler.spec.CrawlSpecLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 회사별 수집을 실행하고 결과를 이력에 남긴다.
 *
 * <p>회사 하나가 실패해도 나머지는 계속 수집한다. 네트워크 호출이 섞여 있어 트랜잭션은 걸지 않고,
 * 적재만 {@link JobPostingIngestService} 안에서 트랜잭션으로 묶는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobPostingCrawlService {

    private final CrawlSpecLoader specLoader;
    private final DeclarativeCrawler crawler;
    private final JobPostingIngestService ingestService;
    private final CrawlRunRepository crawlRunRepository;
    private final TechGraphService techGraphService;

    /** 명세에 있는 회사를 순서대로 수집한다. 사이트에 부담을 주지 않도록 동시에 돌리지 않는다. */
    public List<CrawlRun> crawlAll() {
        List<CrawlSpec> specs = specLoader.loadAll();
        log.info("채용공고 수집을 시작합니다. 회사 {}곳", specs.size());

        List<CrawlRun> runs = new ArrayList<>(specs.size());
        for (CrawlSpec spec : specs) {
            runs.add(crawl(spec));
        }

        long succeeded = runs.stream().filter(CrawlRun::isSuccess).count();
        log.info("채용공고 수집을 마쳤습니다. 성공 {}/{}곳", succeeded, runs.size());

        // 모집 중인 공고가 바뀌었으니 함께 요구되는 기술 관계도 다시 계산한다.
        techGraphService.rebuildAfterCrawl();
        return runs;
    }

    public CrawlRun crawl(String company) {
        return crawl(specLoader.load(company));
    }

    public CrawlRun crawl(CrawlSpec spec) {
        LocalDateTime startedAt = LocalDateTime.now();
        try {
            CrawlResult result = crawler.collect(spec);
            IngestSummary summary = ingestService.ingest(spec, result, startedAt);

            log.info("[{}] 수집 완료: 수집 {} / 신규 {} / 갱신 {} / 유지 {} / 마감 {} / 실패 {}",
                    spec.company(), summary.collected(), summary.created(), summary.updated(),
                    summary.unchanged(), summary.closed(), summary.failed());

            return crawlRunRepository.save(CrawlRun.completed(spec.company(), summary,
                    (int) result.detailFailureCount(), result.issues(), startedAt, LocalDateTime.now()));
        } catch (Exception e) {
            log.error("[{}] 수집에 실패했습니다.", spec.company(), e);
            return crawlRunRepository.save(CrawlRun.failed(spec.company(),
                    "%s: %s".formatted(e.getClass().getSimpleName(), e.getMessage()),
                    startedAt, LocalDateTime.now()));
        }
    }
}
