package com.roddy.global.client.analysis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 역량 분석 AI 서버(roddy-ai)를 부른다.
 *
 * <p>같은 도커 네트워크 안에서만 닿는 내부 서비스다. 깃허브를 훑고 LLM 을 부르느라 한참 걸리므로
 * 넉넉한 시간을 준다.
 */
@Slf4j
@Component
public class AnalysisAiClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";
    private static final String ANALYSES_PATH = "/internal/analyses";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(3);

    private final RestClient restClient;
    private final String internalSecret;

    public AnalysisAiClient(@Value("${app.ai.base-url}") String baseUrl,
                            @Value("${app.ai.internal-secret}") String internalSecret) {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException("app.ai.internal-secret 이 필요합니다.");
        }
        this.internalSecret = internalSecret;

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public AnalysisAiResponse analyze(AnalysisAiRequest request) {
        log.info("역량 분석을 요청합니다. userId={}", request.userId());

        return restClient.post()
                .uri(ANALYSES_PATH)
                .header(INTERNAL_SECRET_HEADER, internalSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AnalysisAiResponse.class);
    }
}
