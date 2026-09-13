package com.roddy.domain.jobposting.service;

import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 전체 수집을 한 번에 하나만 돌린다.
 *
 * <p>예약 수집과 어드민이 누른 수집이 겹치면 같은 사이트에 요청이 두 배로 나가고, 같은 공고를 동시에
 * 적재하느라 서로의 결과를 덮어쓴다. 그래서 둘 다 이곳을 거쳐 시작한다.
 *
 * <p>서버 한 대에서 도는 것을 전제로 메모리의 표시 하나로 막는다. 여러 대로 늘리면 DB 락 같은 공유
 * 잠금으로 바꿔야 한다.
 */
@Slf4j
@Component
public class JobPostingCrawlLauncher {

    private final JobPostingCrawlService crawlService;
    private final Executor crawlExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public JobPostingCrawlLauncher(JobPostingCrawlService crawlService,
                                   @Qualifier("crawlExecutor") Executor crawlExecutor) {
        this.crawlService = crawlService;
        this.crawlExecutor = crawlExecutor;
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * 어드민이 요청한 수집. 수 분이 걸리므로 요청 스레드를 붙잡지 않고 뒤에서 돌린다.
     *
     * @throws GeneralException 이미 수집이 돌고 있을 때
     */
    public void startInBackground() {
        if (!running.compareAndSet(false, true)) {
            throw new GeneralException(GeneralErrorCode.CRAWL_ALREADY_RUNNING);
        }

        try {
            crawlExecutor.execute(this::crawlAndRelease);
        } catch (RejectedExecutionException e) {
            // 시작도 못 했는데 표시를 남기면 다음 수집이 영영 막힌다.
            running.set(false);
            log.error("채용공고 수집을 시작하지 못했습니다.", e);
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 예약 수집. 스케줄러 스레드에서 그대로 돈다. 이미 돌고 있으면 기다리지 않고 건너뛴다.
     *
     * @return 수집을 돌렸으면 true
     */
    public boolean runIfIdle() {
        if (!running.compareAndSet(false, true)) {
            log.warn("이미 채용공고 수집이 진행 중이라 이번 예약 수집은 건너뜁니다.");
            return false;
        }

        crawlAndRelease();
        return true;
    }

    private void crawlAndRelease() {
        try {
            crawlService.crawlAll();
        } catch (RuntimeException e) {
            log.error("채용공고 수집이 도중에 멈췄습니다.", e);
        } finally {
            running.set(false);
        }
    }
}
