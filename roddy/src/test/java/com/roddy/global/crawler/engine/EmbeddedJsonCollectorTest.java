package com.roddy.global.crawler.engine;

import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.CrawlSpecFixtures;
import com.roddy.global.crawler.spec.CrawlSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EmbeddedJsonCollectorTest {

    private MockRestServiceServer server;
    private EmbeddedJsonCollector collector;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        collector = new EmbeddedJsonCollector(new CrawlHttpClient(builder.build()));
    }

    @Test
    @DisplayName("React Query 캐시에서 queryKey 가 일치하는 항목의 공고 배열을 꺼낸다")
    void collectsFromMatchedQuery() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: kakaopay
                source_type: embedded_json
                list:
                  url: https://example.career.greetinghr.com/ko/main
                  script_id: __NEXT_DATA__
                  select:
                    array: props.pageProps.dehydratedState.queries
                    match_field: queryKey
                    match_value: ["openings"]
                    take: state.data
                fields:
                  job_id: openingId
                  title: title
                  location: openingJobPosition.openingJobPositions[].workspacePlace.place
                apply_url:
                  template: https://example.career.greetinghr.com/ko/o/{job_id}
                """);

        server.expect(requestTo("https://example.career.greetinghr.com/ko/main"))
                .andRespond(withSuccess(greetingPage(), MediaType.TEXT_HTML));

        List<CrawlRecord> records = collector.collect(spec);

        server.verify();
        assertThat(records).hasSize(2);
        assertThat(records.getFirst().text("job_id")).isEqualTo("111");
        assertThat(records.getFirst().text("location")).isEqualTo("판교");
        assertThat(records.getFirst().text(CrawlRecord.APPLY_URL))
                .isEqualTo("https://example.career.greetinghr.com/ko/o/111");
    }

    @Test
    @DisplayName("match_prefix 는 queryKey 앞부분만 맞으면 고른다")
    void matchesByPrefix() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: example
                source_type: embedded_json
                list:
                  url: https://example.com/jobs
                  select:
                    array: props.queries
                    match_field: queryKey
                    match_prefix: [career, getOpenings]
                    take: state.data
                fields: {job_id: id, title: title}
                """);

        server.expect(requestTo("https://example.com/jobs"))
                .andRespond(withSuccess("""
                        <script id="__NEXT_DATA__">
                        {"props": {"queries": [
                          {"queryKey": ["career", "getSomethingElse"], "state": {"data": []}},
                          {"queryKey": ["career", "getOpenings", {"page": 1}],
                           "state": {"data": [{"id": "9", "title": "백엔드"}]}}
                        ]}}
                        </script>
                        """, MediaType.TEXT_HTML));

        List<CrawlRecord> records = collector.collect(spec);

        server.verify();
        assertThat(records).extracting(record -> record.text("job_id")).containsExactly("9");
    }

    @Test
    @DisplayName("맞는 항목이 없으면 빈 결과를 돌려준다")
    void returnsEmptyWhenNothingMatches() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: example
                source_type: embedded_json
                list:
                  url: https://example.com/jobs
                  select:
                    array: props.queries
                    match_field: queryKey
                    match_value: ["openings"]
                    take: state.data
                fields: {job_id: id, title: title}
                """);

        server.expect(requestTo("https://example.com/jobs"))
                .andRespond(withSuccess("""
                        <script id="__NEXT_DATA__">{"props": {"queries": []}}</script>
                        """, MediaType.TEXT_HTML));

        assertThat(collector.collect(spec)).isEmpty();
    }

    private String greetingPage() {
        return """
                <html><body>
                <script id="__NEXT_DATA__">
                {"props": {"pageProps": {"dehydratedState": {"queries": [
                  {"queryKey": ["config"], "state": {"data": {"theme": "light"}}},
                  {"queryKey": ["openings"], "state": {"data": [
                    {"openingId": "111", "title": "백엔드 개발자",
                     "openingJobPosition": {"openingJobPositions": [
                       {"workspacePlace": {"place": "판교"}}
                     ]}},
                    {"openingId": "222", "title": "프론트엔드 개발자",
                     "openingJobPosition": {"openingJobPositions": [
                       {"workspacePlace": {"place": "서울"}}
                     ]}}
                  ]}}
                ]}}}}
                </script>
                </body></html>
                """;
    }
}
