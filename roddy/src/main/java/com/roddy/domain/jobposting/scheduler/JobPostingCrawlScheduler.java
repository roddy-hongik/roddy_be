package com.roddy.domain.jobposting.scheduler;

import com.roddy.domain.jobposting.service.JobPostingCrawlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 정해진 시각에 전체 회사를 수집한다.
 *
 * <p>실제 채용 사이트로 요청이 나가므로 기본값은 꺼짐이다. 운영에서 의도적으로 켠다
 * ({@code app.crawler.scheduler.enabled}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.crawler.scheduler", name = "enabled", havingValue = "true")
public class JobPostingCrawlScheduler {

    private final JobPostingCrawlService crawlService;

    @Scheduled(cron = "${app.crawler.scheduler.cron}", zone = "Asia/Seoul")
    public void crawlAll() {
        log.info("예약된 채용공고 수집을 시작합니다.");
        crawlService.crawlAll();
    }
}
