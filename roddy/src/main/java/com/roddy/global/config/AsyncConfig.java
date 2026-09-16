package com.roddy.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 역량 분석 전용 스레드 풀.
     *
     * <p>분석 한 건이 깃허브 호출과 LLM 응답을 기다리느라 수십 초를 쓴다. 공용 풀을 쓰면 다른 비동기
     * 작업까지 같이 막히므로 따로 둔다. 큐를 크게 잡지 않은 것은, 밀리면 사용자에게 실패를 알리는
     * 편이 언제 끝날지 모르게 기다리게 하는 것보다 낫기 때문이다.
     */
    @Bean
    public Executor analysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("analysis-");
        executor.initialize();
        return executor;
    }

    /**
     * 채용공고 수집 전용 스레드.
     *
     * <p>수집은 회사를 하나씩 차례로 돌며 수 분이 걸린다. 사이트에 부담을 주지 않으려고 동시에 두 번
     * 돌리지 않으므로 스레드는 하나면 된다. 겹치는 요청은 스레드 풀에 닿기 전에
     * {@code JobPostingCrawlLauncher} 가 막는다.
     */
    @Bean
    public Executor crawlExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        // 수집이 끝나 표시를 내린 직후, 스레드가 풀로 돌아오기 전에 다음 요청이 올 수 있다. 그 한 건은 받아 둔다.
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("crawl-");
        executor.initialize();
        return executor;
    }
}
