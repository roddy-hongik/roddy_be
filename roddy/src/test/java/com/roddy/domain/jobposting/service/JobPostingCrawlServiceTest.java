package com.roddy.domain.jobposting.service;

import com.roddy.domain.graph.service.TechGraphService;
import com.roddy.domain.jobposting.dto.IngestSummary;
import com.roddy.domain.jobposting.entity.CrawlRun;
import com.roddy.domain.jobposting.enums.CrawlRunStatus;
import com.roddy.domain.jobposting.repository.CrawlRunRepository;
import com.roddy.global.crawler.CrawlResult;
import com.roddy.global.crawler.CrawlSpecFixtures;
import com.roddy.global.crawler.engine.DeclarativeCrawler;
import com.roddy.global.crawler.spec.CrawlSpec;
import com.roddy.global.crawler.spec.CrawlSpecLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobPostingCrawlServiceTest {

    @Mock
    private CrawlSpecLoader specLoader;

    @Mock
    private DeclarativeCrawler crawler;

    @Mock
    private JobPostingIngestService ingestService;

    @Mock
    private CrawlRunRepository crawlRunRepository;

    @Mock
    private TechGraphService techGraphService;

    @InjectMocks
    private JobPostingCrawlService crawlService;

    private final CrawlSpec kakao = spec("kakao");
    private final CrawlSpec naver = spec("naver");

    @BeforeEach
    void setUp() {
        given(crawlRunRepository.save(any(CrawlRun.class))).willAnswer(call -> call.getArgument(0));
    }

    @Test
    @DisplayName("수집과 적재가 끝나면 결과를 이력에 남긴다")
    void recordsSuccessfulRun() {
        given(crawler.collect(kakao)).willReturn(new CrawlResult("kakao", List.of(), List.of()));
        given(ingestService.ingest(eq(kakao), any(CrawlResult.class), any(LocalDateTime.class)))
                .willReturn(new IngestSummary(50, 5, 3, 42, 1, 0));

        CrawlRun run = crawlService.crawl(kakao);

        assertThat(run.getStatus()).isEqualTo(CrawlRunStatus.SUCCESS);
        assertThat(run.getCompanyCode()).isEqualTo("kakao");
        assertThat(run.getCreatedCount()).isEqualTo(5);
        assertThat(run.getClosedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("회사 하나가 실패해도 나머지 회사는 계속 수집한다")
    void keepsGoingAfterCompanyFailure() {
        given(specLoader.loadAll()).willReturn(List.of(kakao, naver));
        willThrow(new RestClientException("503 Service Unavailable")).given(crawler).collect(kakao);
        given(crawler.collect(naver)).willReturn(new CrawlResult("naver", List.of(), List.of()));
        given(ingestService.ingest(eq(naver), any(CrawlResult.class), any(LocalDateTime.class)))
                .willReturn(new IngestSummary(30, 30, 0, 0, 0, 0));

        List<CrawlRun> runs = crawlService.crawlAll();

        assertThat(runs).hasSize(2);
        assertThat(runs.getFirst().getStatus()).isEqualTo(CrawlRunStatus.FAILED);
        assertThat(runs.getFirst().getMessage()).contains("503");
        assertThat(runs.get(1).getStatus()).isEqualTo(CrawlRunStatus.SUCCESS);
        assertThat(runs.get(1).getCreatedCount()).isEqualTo(30);
        verify(techGraphService).rebuildAfterCrawl();
    }

    @Test
    @DisplayName("수집 점검에 걸리면 일부 실패로 남긴다")
    void recordsPartialWhenCheckFails() {
        given(crawler.collect(kakao))
                .willReturn(new CrawlResult("kakao", List.of(), List.of("0건 수집 — url / 응답 경로 / 필터를 확인해야 합니다.")));
        given(ingestService.ingest(eq(kakao), any(CrawlResult.class), any(LocalDateTime.class)))
                .willReturn(IngestSummary.empty());

        CrawlRun run = crawlService.crawl(kakao);

        assertThat(run.getStatus()).isEqualTo(CrawlRunStatus.PARTIAL);
        assertThat(run.getMessage()).contains("0건 수집");
    }

    private CrawlSpec spec(String company) {
        return CrawlSpecFixtures.spec("""
                company: %s
                source_type: json
                list: {url: https://example.com, response_path: jobList}
                fields: {job_id: id, title: title}
                """.formatted(company));
    }
}
