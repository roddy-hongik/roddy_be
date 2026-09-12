package com.roddy.global.crawler.engine;

import com.roddy.global.crawler.CrawlResult;
import com.roddy.global.crawler.CrawlSpecFixtures;
import com.roddy.global.crawler.spec.CrawlSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeclarativeCrawlerTest {

    private MockRestServiceServer server;
    private DeclarativeCrawler crawler;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        crawler = new DeclarativeCrawler(new CrawlHttpClient(builder.build()));
    }

    @Test
    @DisplayName("수집한 뒤 필터를 적용하고 점검 결과까지 함께 돌려준다")
    void collectsFiltersAndChecks() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: kakao
                source_type: json
                list:
                  url: https://example.com/api/job-list
                  response_path: jobList
                required: [job_id, title]
                fields: {job_id: realId, title: jobOfferTitle}
                filter:
                  field: title
                  exclude_contains: [인재풀]
                """);

        server.expect(requestTo("https://example.com/api/job-list"))
                .andRespond(withSuccess("""
                        {"jobList": [
                          {"realId": "P-1", "jobOfferTitle": "백엔드 개발자"},
                          {"realId": "P-2", "jobOfferTitle": "개발 인재풀"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        CrawlResult result = crawler.collect(spec);

        server.verify();
        assertThat(result.company()).isEqualTo("kakao");
        assertThat(result.size()).isEqualTo(1);
        assertThat(result.isHealthy()).isTrue();
    }

    @Test
    @DisplayName("응답 경로가 어긋나 0건이면 점검에서 걸린다")
    void reportsBrokenSpec() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: kakao
                source_type: json
                list:
                  url: https://example.com/api/job-list
                  response_path: jobList
                required: [job_id, title]
                fields: {job_id: realId, title: jobOfferTitle}
                """);

        server.expect(requestTo("https://example.com/api/job-list"))
                .andRespond(withSuccess("""
                        {"items": [{"realId": "P-1", "jobOfferTitle": "백엔드 개발자"}]}
                        """, MediaType.APPLICATION_JSON));

        CrawlResult result = crawler.collect(spec);

        assertThat(result.isHealthy()).isFalse();
        assertThat(result.issues()).singleElement().asString().contains("0건 수집");
    }

    @Test
    @DisplayName("아직 지원하지 않는 source_type 은 명확히 거절한다")
    void rejectsUnsupportedSourceType() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: example
                source_type: html
                list: {url: https://example.com/jobs, row_selector: .job}
                fields: {job_id: .id, title: .title}
                """);

        assertThatThrownBy(() -> crawler.collect(spec))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("html");
    }
}
