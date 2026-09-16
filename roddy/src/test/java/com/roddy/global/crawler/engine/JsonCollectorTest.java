package com.roddy.global.crawler.engine;

import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.CrawlSpecFixtures;
import com.roddy.global.crawler.spec.CrawlSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class JsonCollectorTest {

    private MockRestServiceServer server;
    private JsonCollector collector;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        collector = new JsonCollector(new CrawlHttpClient(builder.build()));
    }

    @Test
    @DisplayName("페이지네이션이 없으면 한 번만 호출한다")
    void collectsWithoutPagination() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: kakao
                source_type: json
                list:
                  url: https://example.com/api/job-list
                  response_path: jobList
                pagination: {type: none}
                fields: {job_id: realId, title: jobOfferTitle}
                """);

        server.expect(requestTo("https://example.com/api/job-list"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"jobList": [{"realId": "P-1", "jobOfferTitle": "백엔드"},
                                     {"realId": "P-2", "jobOfferTitle": "프론트"}]}
                        """, MediaType.APPLICATION_JSON));

        List<CrawlRecord> records = collector.collect(spec);

        server.verify();
        assertThat(records).extracting(record -> record.text("job_id")).containsExactly("P-1", "P-2");
        // charset 을 알려주지 않는 응답도 UTF-8 로 읽어야 한글이 깨지지 않는다.
        assertThat(records).extracting(record -> record.text("title")).containsExactly("백엔드", "프론트");
    }

    @Test
    @DisplayName("page_number 방식은 전체 페이지 수까지 넘긴다")
    void collectsByPageNumber() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: example
                source_type: json
                list:
                  url: https://example.com/api/jobs
                  response_path: data
                pagination:
                  type: page_number
                  param: page
                  start: 1
                  total_pages_path: totalPage
                fields: {job_id: id, title: title}
                """);

        // 전체 페이지 수가 문자열로 오는 사이트가 있어 숫자 변환까지 확인한다.
        server.expect(requestTo("https://example.com/api/jobs?page=1"))
                .andRespond(withSuccess("""
                        {"totalPage": "2", "data": [{"id": "1", "title": "백엔드"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.com/api/jobs?page=2"))
                .andRespond(withSuccess("""
                        {"totalPage": "2", "data": [{"id": "2", "title": "프론트"}]}
                        """, MediaType.APPLICATION_JSON));

        List<CrawlRecord> records = collector.collect(spec);

        server.verify();
        assertThat(records).extracting(record -> record.text("job_id")).containsExactly("1", "2");
    }

    @Test
    @DisplayName("offset 방식은 빈 응답을 만날 때까지 받은 건수만큼 전진한다")
    void collectsByOffset() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: naver
                source_type: json
                list:
                  url: https://example.com/api/jobs
                  response_path: list
                pagination:
                  type: offset
                  param: firstIndex
                  start: 0
                fields: {job_id: id, title: title}
                """);

        server.expect(requestTo("https://example.com/api/jobs?firstIndex=0"))
                .andRespond(withSuccess("""
                        {"list": [{"id": "1", "title": "백엔드"}, {"id": "2", "title": "프론트"}]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://example.com/api/jobs?firstIndex=2"))
                .andRespond(withSuccess("""
                        {"list": []}
                        """, MediaType.APPLICATION_JSON));

        List<CrawlRecord> records = collector.collect(spec);

        server.verify();
        assertThat(records).hasSize(2);
    }

    @Test
    @DisplayName("POST 로 받는 회사는 헤더와 본문을 명세대로 보낸다")
    void sendsHeadersAndBody() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: kakaobank
                source_type: json
                list:
                  url: https://example.com/api/recruits
                  method: POST
                  headers:
                    Referer: https://example.com/jobs
                  body:
                    pageNumber: 1
                    pageSize: 100
                  response_path: list
                fields: {job_id: id, title: title}
                """);

        server.expect(requestTo("https://example.com/api/recruits"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Referer", "https://example.com/jobs"))
                .andExpect(content().json("""
                        {"pageNumber": 1, "pageSize": 100}
                        """))
                .andRespond(withSuccess("""
                        {"list": [{"id": "1", "title": "백엔드"}]}
                        """, MediaType.APPLICATION_JSON));

        List<CrawlRecord> records = collector.collect(spec);

        server.verify();
        assertThat(records).hasSize(1);
    }

    @Test
    @DisplayName("record_path 가 있으면 항목 안쪽을 한 단계 더 들어간다")
    void unwrapsRecordPath() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: toss
                source_type: json
                list:
                  url: https://example.com/api/job-groups
                  response_path: success
                  record_path: primary_job
                fields: {job_id: id, title: title}
                """);

        server.expect(requestTo("https://example.com/api/job-groups"))
                .andRespond(withSuccess("""
                        {"success": [
                          {"primary_job": {"id": "4321", "title": "서버 개발자"}},
                          {"other": {}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<CrawlRecord> records = collector.collect(spec);

        server.verify();
        assertThat(records).extracting(record -> record.text("job_id")).containsExactly("4321");
    }
}
