package com.roddy.global.crawler.engine;

import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.CrawlResult;
import com.roddy.global.crawler.CrawlSpecFixtures;
import com.roddy.global.crawler.spec.CrawlSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

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
        crawler = new DeclarativeCrawler(new CrawlHttpClient(builder.build()), Duration.ZERO);
    }

    @Test
    @DisplayName("목록을 받은 뒤 공고마다 상세 본문까지 채운다")
    void collectsListThenDetail() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: woowahan
                source_type: json
                list:
                  url: https://example.com/w1/recruits
                  response_path: data.list
                required: [job_id, title]
                fields: {job_id: id, title: title, recruit_number: recruitNumber}
                detail:
                  enabled: true
                  source_type: json
                  url_template: https://example.com/w1/recruits/{recruit_number}
                  fields: {description: data.recruitContents}
                """);

        server.expect(requestTo("https://example.com/w1/recruits"))
                .andRespond(withSuccess("""
                        {"data": {"list": [
                          {"id": "1", "title": "서버 개발자", "recruitNumber": "R1"},
                          {"id": "2", "title": "안드로이드 개발자", "recruitNumber": "R2"}
                        ]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.com/w1/recruits/R1"))
                .andRespond(withSuccess("""
                        {"data": {"recruitContents": "서버 개발자 본문"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.com/w1/recruits/R2"))
                .andRespond(withSuccess("""
                        {"data": {"recruitContents": "안드로이드 개발자 본문"}}
                        """, MediaType.APPLICATION_JSON));

        CrawlResult result = crawler.collect(spec);

        server.verify();
        assertThat(result.isHealthy()).isTrue();
        assertThat(result.detailFailureCount()).isZero();
        assertThat(result.records())
                .extracting(record -> record.text(CrawlRecord.DESCRIPTION))
                .containsExactly("서버 개발자 본문", "안드로이드 개발자 본문");
    }

    @Test
    @DisplayName("상세 건수를 제한하면 목록은 전부, 상세는 일부만 받는다")
    void limitsDetailRequests() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: woowahan
                source_type: json
                list:
                  url: https://example.com/w1/recruits
                  response_path: data.list
                required: [job_id, title]
                fields: {job_id: id, title: title}
                detail:
                  enabled: true
                  source_type: json
                  url_template: https://example.com/w1/recruits/{job_id}
                  fields: {description: data.recruitContents}
                """);

        server.expect(requestTo("https://example.com/w1/recruits"))
                .andRespond(withSuccess("""
                        {"data": {"list": [
                          {"id": "1", "title": "서버 개발자"},
                          {"id": "2", "title": "안드로이드 개발자"}
                        ]}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.com/w1/recruits/1"))
                .andRespond(withSuccess("""
                        {"data": {"recruitContents": "서버 개발자 본문"}}
                        """, MediaType.APPLICATION_JSON));

        CrawlResult result = crawler.collect(spec, 1);

        server.verify();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.records().get(1).value(CrawlRecord.DESCRIPTION)).isNull();
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
