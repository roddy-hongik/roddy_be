package com.roddy.global.client.interview;

import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 HTTP 로 불러 AI 서버(roddy_ai)와 맞춘 snake_case 계약을 확인한다. */
class InterviewAiClientTest {

    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> secretHeader = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void AI_서버의_계약대로_snake_case_로_요청하고_질문을_읽는다() throws Exception {
        start(200, """
                {"questions": [
                  {"id": "q1", "question": "Redis 캐시 무효화 전략은?", "intent": "일관성", "key_points": ["TTL", "삭제"]},
                  {"id": "q2", "question": "분산 락은 언제 필요한가?", "intent": "동시성", "key_points": ["경합"]},
                  {"id": "q3", "question": "캐시 장애에 어떻게 대응하는가?", "intent": "운영", "key_points": ["fallback"]}
                ]}
                """);

        InterviewAiResponse response = client().generate(new InterviewAiRequest(
                List.of("Java"), List.of("Redis"), "백엔드 개발자", "토스"));

        assertThat(secretHeader.get()).isEqualTo("secret");
        assertThat(requestBody.get())
                .contains("\"current_skills\"", "\"gap_skills\"", "\"target_job\"", "\"target_company\"");
        assertThat(response.questions()).hasSize(3);
        assertThat(response.questions().getFirst().id()).isEqualTo("q1");
        assertThat(response.questions().getFirst().keyPoints()).containsExactly("TTL", "삭제");
    }

    @Test
    void AI_서버가_오류로_답하면_서비스를_잠시_쓸_수_없다고_던진다() throws Exception {
        start(404, "{\"detail\":\"Not Found\"}");

        assertThatThrownBy(() -> client().generate(new InterviewAiRequest(
                List.of(), List.of("Redis"), "백엔드 개발자", null)))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE));
    }

    private void start(int status, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/interview-questions", exchange -> {
            secretHeader.set(exchange.getRequestHeaders().getFirst("X-Internal-Secret"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();
    }

    private InterviewAiClient client() {
        return new InterviewAiClient("http://localhost:" + server.getAddress().getPort(), "secret");
    }
}
