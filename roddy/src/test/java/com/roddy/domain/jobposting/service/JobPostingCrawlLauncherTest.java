package com.roddy.domain.jobposting.service;

import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class JobPostingCrawlLauncherTest {

    private final JobPostingCrawlService crawlService = mock(JobPostingCrawlService.class);

    /** 넘겨받은 작업을 바로 돌리지 않고 쌓아 둔다. 수집이 도는 중인 상태를 만들기 위해서다. */
    private final Deque<Runnable> pending = new ArrayDeque<>();

    private final JobPostingCrawlLauncher launcher = new JobPostingCrawlLauncher(crawlService, pending::add);

    @Test
    @DisplayName("수집이 도는 동안에는 새로 시작하지 않는다")
    void rejectsSecondStartWhileRunning() {
        launcher.startInBackground();

        assertThat(launcher.isRunning()).isTrue();
        assertThatThrownBy(launcher::startInBackground)
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getCode()).isEqualTo(GeneralErrorCode.CRAWL_ALREADY_RUNNING));
        assertThat(pending).hasSize(1);
    }

    @Test
    @DisplayName("수집이 끝나면 다시 시작할 수 있다")
    void allowsStartAfterFinished() {
        launcher.startInBackground();
        pending.poll().run();

        assertThat(launcher.isRunning()).isFalse();
        launcher.startInBackground();
        assertThat(pending).hasSize(1);
        verify(crawlService).crawlAll();
    }

    @Test
    @DisplayName("수집이 예외로 멈춰도 다음 수집이 막히지 않는다")
    void releasesAfterFailure() {
        given(crawlService.crawlAll()).willThrow(new IllegalStateException("명세를 읽지 못했습니다"));

        launcher.startInBackground();
        pending.poll().run();

        assertThat(launcher.isRunning()).isFalse();
    }

    @Test
    @DisplayName("스레드 풀이 작업을 받지 않으면 표시를 되돌린다")
    void releasesWhenRejected() {
        JobPostingCrawlLauncher rejecting = new JobPostingCrawlLauncher(crawlService, task -> {
            throw new RejectedExecutionException("가득 찼습니다");
        });

        assertThatThrownBy(rejecting::startInBackground)
                .isInstanceOfSatisfying(GeneralException.class,
                        e -> assertThat(e.getCode()).isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE));
        assertThat(rejecting.isRunning()).isFalse();
    }

    @Test
    @DisplayName("예약 수집은 이미 돌고 있으면 기다리지 않고 건너뛴다")
    void scheduledRunSkipsWhileRunning() {
        launcher.startInBackground();

        assertThat(launcher.runIfIdle()).isFalse();
        verify(crawlService, never()).crawlAll();
    }

    @Test
    @DisplayName("예약 수집은 비어 있으면 그 자리에서 돌고 표시를 내린다")
    void scheduledRunRunsInline() {
        assertThat(launcher.runIfIdle()).isTrue();

        verify(crawlService).crawlAll();
        assertThat(launcher.isRunning()).isFalse();
    }
}
