package com.roddy.global.client.analysis;

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

/** 실제 HTTP 로 불러 AI 서버(roddy_ai)와 맞춘 snake_case 계약을 확인한다. */
class AnalysisAiClientTest {

    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void 분석_재료를_snake_case_로_보내고_snake_case_리포트를_읽는다() throws Exception {
        start("""
                {
                  "title": "백엔드 기초를 갖춘 주니어",
                  "total_score": 64,
                  "summary": "요약",
                  "github_analysis": "깃허브 분석",
                  "portfolio_analysis": "포트폴리오 분석",
                  "stacks": [{"name": "Java", "score": 72, "level": "INTERMEDIATE", "description": "근거",
                              "category": "DATA_MODELING", "found_in": ["GITHUB", "PORTFOLIO"]}],
                  "categories": [{"code": "DATA_MODELING", "score": 58, "interpretation": "해석"}],
                  "sources": {"repository_count": 12, "portfolio_included": true, "warnings": ["경고"]}
                }
                """);

        AnalysisAiResponse response = client().analyze(new AnalysisAiRequest(
                1L, "https://github.com/octocat", null, "https://s3.example.com/resume.pdf", "resume.pdf",
                "BACKEND", "JUNIOR", List.of(new AnalysisAiRequest.Category("DATA_MODELING", "데이터 설계", "설명"))));

        assertThat(requestBody.get())
                .contains("\"user_id\":1", "\"github_url\"", "\"portfolio_url\"", "\"portfolio_file_name\"",
                        "\"desired_job\"", "\"experience_years\"", "\"categories\"")
                .doesNotContain("github_token");
        assertThat(response.totalScore()).isEqualTo(64);
        assertThat(response.githubAnalysis()).isEqualTo("깃허브 분석");
        assertThat(response.portfolioAnalysis()).isEqualTo("포트폴리오 분석");
        assertThat(response.stacks().getFirst().foundInPortfolio()).isTrue();
        assertThat(response.categories().getFirst().score()).isEqualTo(58);
        assertThat(response.sources().repositoryCount()).isEqualTo(12);
        assertThat(response.sources().portfolioIncluded()).isTrue();
    }

    private void start(String responseBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/internal/analyses", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream body = exchange.getResponseBody()) {
                body.write(bytes);
            }
        });
        server.start();
    }

    private AnalysisAiClient client() {
        return new AnalysisAiClient("http://localhost:" + server.getAddress().getPort(), "secret");
    }
}
