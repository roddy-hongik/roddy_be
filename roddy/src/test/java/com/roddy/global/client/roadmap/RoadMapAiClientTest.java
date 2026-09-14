package com.roddy.global.client.roadmap;

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

/** 실제 HTTP 로 불러 AI 서버(roddy_ai)와 맞춘 계약을 확인한다. */
class RoadMapAiClientTest {

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
    void AI_서버의_계약대로_snake_case_로_요청하고_응답을_읽는다() throws Exception {
        start(200, """
                {"title":"백엔드 성장 로드맵","steps":[{"stage":"기초","goal":"목표","topics":["Redis"],"outputs":["예제"]}]}
                """);

        RoadMapAiResponse response = client().generate(new RoadMapAiRequest(
                List.of("Java"), List.of("Redis"), "백엔드 개발자", "토스"));

        assertThat(secretHeader.get()).isEqualTo("secret");
        assertThat(requestBody.get())
                .contains("\"current_skills\"", "\"gap_skills\"", "\"target_job\"", "\"target_company\"");
        assertThat(response.title()).isEqualTo("백엔드 성장 로드맵");
        assertThat(response.steps().getFirst().topics()).containsExactly("Redis");
    }

    @Test
    void AI_서버가_오류로_답하면_서비스를_잠시_쓸_수_없다고_던진다() throws Exception {
        start(500, "{\"detail\":\"Internal Server Error\"}");

        assertThatThrownBy(() -> client().generate(new RoadMapAiRequest(
                List.of(), List.of("Redis"), "백엔드 개발자", null)))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE));
    }

    @Test
    void AI_서버에_연결되지_않으면_서비스를_잠시_쓸_수_없다고_던진다() throws Exception {
        // 0번 포트에는 연결할 수 없다. 잠깐 열었다 닫은 포트는 그 사이 다른 프로세스가 잡을 수 있어 쓰지 않는다.
        RoadMapAiClient unreachable = new RoadMapAiClient("http://localhost:0", "secret");

        assertThatThrownBy(() -> unreachable.generate(new RoadMapAiRequest(
                List.of(), List.of("Redis"), "백엔드 개발자", null)))
                .isInstanceOfSatisfying(GeneralException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(GeneralErrorCode.SERVICE_UNAVAILABLE));
    }

    private void start(int status, String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/roadmaps", exchange -> {
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

    private RoadMapAiClient client() {
        return new RoadMapAiClient("http://localhost:" + server.getAddress().getPort(), "secret");
    }
}
