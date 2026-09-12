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
}
