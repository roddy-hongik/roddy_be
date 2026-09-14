package com.roddy.global.client.interview;

import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

@Slf4j
@Component
public class InterviewAiClient {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofMinutes(3);

    private final RestClient restClient;
    private final String internalSecret;

    public InterviewAiClient(@Value("${app.ai.base-url}") String baseUrl,
                             @Value("${app.ai.internal-secret}") String internalSecret) {
        if (internalSecret == null || internalSecret.isBlank()) {
            throw new IllegalStateException("app.ai.internal-secret 이 필요합니다.");
        }
        this.internalSecret = internalSecret;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    public InterviewAiResponse generate(InterviewAiRequest request) {
        try {
            return restClient.post()
                    .uri("/internal/interview-questions")
                    .header(INTERNAL_SECRET_HEADER, internalSecret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(InterviewAiResponse.class);
        } catch (RestClientResponseException exception) {
            // AI 서버가 오류로 답했다. 사용자의 요청 탓이 아니므로 잠시 쓸 수 없다고 답한다.
            log.warn("모의면접 질문 생성 요청이 실패했습니다. status={}", exception.getStatusCode().value());
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE);
        }
    }
}
