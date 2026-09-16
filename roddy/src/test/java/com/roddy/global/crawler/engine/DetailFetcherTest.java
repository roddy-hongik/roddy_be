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

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DetailFetcherTest {

    private MockRestServiceServer server;
    private DetailFetcher fetcher;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        fetcher = new DetailFetcher(new CrawlHttpClient(builder.build()), Duration.ZERO);
    }

    @Test
    @DisplayName("조립한 주소로 JSON 상세를 받아 본문을 채운다")
    void fillsDescriptionFromJsonDetail() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: woowahan
                source_type: json
                list: {url: https://example.com/api/recruits, response_path: data.list}
                fields: {job_id: id, recruit_number: recruitNumber}
                detail:
                  enabled: true
                  source_type: json
                  url_template: https://example.com/w1/recruits/{recruit_number}
                  fields:
                    description: data.recruitContents
                """);

        server.expect(requestTo("https://example.com/w1/recruits/R2026001"))
                .andRespond(withSuccess("""
                        {"data": {"recruitContents": "배민 서버 개발자를 찾습니다."}}
                        """, MediaType.APPLICATION_JSON));

        List<CrawlRecord> records = fetcher.fetchAll(
                List.of(record(Map.of("job_id", "1", "recruit_number", "R2026001"))), spec, 0);

        server.verify();
        assertThat(records.getFirst().text(CrawlRecord.DESCRIPTION)).isEqualTo("배민 서버 개발자를 찾습니다.");
        assertThat(records.getFirst().hasDetailError()).isFalse();
    }

    @Test
    @DisplayName("본문이 제목/내용 배열로 오면 읽을 수 있는 글로 편다")
    void flattensSectionedDescription() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: nhn
                source_type: json
                list: {url: https://example.com/v1/job-postings, response_path: result}
                fields: {job_id: id}
                detail:
                  enabled: true
                  source_type: json
                  url_template: https://example.com/v1/job-postings/{job_id}
                  fields:
                    description: result.jobPostingContentsItems
                """);

        server.expect(requestTo("https://example.com/v1/job-postings/7"))
                .andRespond(withSuccess("""
                        {"result": {"jobPostingContentsItems": [
                          {"title": "주요 업무", "contents": ["서버 개발", "API 설계"]},
                          {"title": "자격 요건", "contents": ["Java 3년"]}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        List<CrawlRecord> records = fetcher.fetchAll(List.of(record(Map.of("job_id", "7"))), spec, 0);

        server.verify();
        assertThat(records.getFirst().text(CrawlRecord.DESCRIPTION))
                .isEqualTo("주요 업무\n서버 개발\nAPI 설계\n\n자격 요건\nJava 3년");
    }

    @Test
    @DisplayName("레코드가 들고 있는 지원 URL 로 임베드 JSON 상세를 받는다")
    void fillsDescriptionFromEmbeddedJsonDetail() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: kakaopay
                source_type: embedded_json
                list: {url: https://example.career.greetinghr.com/ko/main}
                fields: {job_id: openingId, title: title}
                detail:
                  enabled: true
                  source_type: embedded_json
                  url_from: apply_url
                  script_id: __NEXT_DATA__
                  select:
                    array: props.pageProps.dehydratedState.queries
                    match_field: queryKey
                    match_prefix: ["career", "getOpeningById"]
                    take: state.data.data
                  fields:
                    description: openingsInfo.detail
                """);

        server.expect(requestTo("https://example.career.greetinghr.com/ko/o/111"))
                .andRespond(withSuccess("""
                        <script id="__NEXT_DATA__">
                        {"props": {"pageProps": {"dehydratedState": {"queries": [
                          {"queryKey": ["career", "getOpeningList"], "state": {"data": {"data": {}}}},
                          {"queryKey": ["career", "getOpeningById", "111"],
                           "state": {"data": {"data": {"openingsInfo": {"detail": "카카오페이 서버 개발자"}}}}}
                        ]}}}}
                        </script>
                        """, MediaType.TEXT_HTML));

        List<CrawlRecord> records = fetcher.fetchAll(List.of(record(Map.of(
                "job_id", "111",
                "apply_url", "https://example.career.greetinghr.com/ko/o/111"))), spec, 0);

        server.verify();
        assertThat(records.getFirst().text(CrawlRecord.DESCRIPTION)).isEqualTo("카카오페이 서버 개발자");
    }

    @Test
    @DisplayName("HTML 상세는 본문 요소의 글을 줄바꿈을 살려 담는다")
    void fillsDescriptionFromHtmlDetail() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: ably
                source_type: json
                list: {url: https://example.com/recruit.json, response_path: pageProps.recruits}
                fields: {job_id: id, apply_url: applyUrl}
                detail:
                  enabled: true
                  source_type: html
                  url_from: apply_url
                  body_selector: '[class*=PageContent]'
                """);

        server.expect(requestTo("https://ably.team/recruit/9"))
                .andRespond(withSuccess("""
                        <html><body>
                        <div class="PageLayout__PageContent">
                          <h2>주요 업무</h2>
                          <p>백엔드 서비스 개발</p>
                        </div>
                        </body></html>
                        """, MediaType.TEXT_HTML));

        List<CrawlRecord> records = fetcher.fetchAll(List.of(record(Map.of(
                "job_id", "9", "apply_url", "https://ably.team/recruit/9"))), spec, 0);

        server.verify();
        assertThat(records.getFirst().text(CrawlRecord.DESCRIPTION))
                .isEqualTo("주요 업무\n백엔드 서비스 개발");
    }

    @Test
    @DisplayName("섹션 설정이 있으면 제목별 본문도 함께 담는다")
    void fillsSectionsFromHtmlDetail() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: naver
                source_type: json
                list: {url: https://example.com/loadJobList.do, response_path: list}
                fields: {job_id: annoId, apply_url: jobDetailLink}
                detail:
                  enabled: true
                  source_type: html
                  url_from: apply_url
                  body_selector: "div.detail_wrap"
                  section_box: "div.detail_box"
                  section_title: "h4.detail_title"
                """);

        server.expect(requestTo("https://recruit.navercorp.com/jobs/1"))
                .andRespond(withSuccess("""
                        <div class="detail_wrap">
                          <div class="detail_box">
                            <h4 class="detail_title">Who We Are</h4>
                            <p class="detail_text">네이버 서치 팀입니다.</p>
                          </div>
                        </div>
                        """, MediaType.TEXT_HTML));

        List<CrawlRecord> records = fetcher.fetchAll(List.of(record(Map.of(
                "job_id", "1", "apply_url", "https://recruit.navercorp.com/jobs/1"))), spec, 0);

        server.verify();
        CrawlRecord fetched = records.getFirst();
        assertThat(fetched.text(CrawlRecord.DESCRIPTION)).contains("네이버 서치 팀입니다.");
        assertThat(fetched.value(CrawlRecord.SECTIONS))
                .isEqualTo(Map.of("Who We Are", "네이버 서치 팀입니다."));
    }

    @Test
    @DisplayName("상세 주소를 만들 수 없으면 요청하지 않고 넘어간다")
    void skipsWhenUrlCannotBeResolved() {
        CrawlSpec spec = CrawlSpecFixtures.spec("""
                company: woowahan
                source_type: json
                list: {url: https://example.com/api, response_path: list}
                fields: {job_id: id}
                detail:
                  enabled: true
                  source_type: json
                  url_template: https://example.com/w1/recruits/{recruit_number}
                  fields: {description: data.recruitContents}
                """);

        Map<String, Object> values = new HashMap<>();
        values.put("job_id", "1");
        values.put("recruit_number", null);

        List<CrawlRecord> records = fetcher.fetchAll(List.of(record(values)), spec, 0);

        server.verify();
        assertThat(records.getFirst().value(CrawlRecord.DESCRIPTION)).isNull();
        assertThat(records.getFirst().hasDetailError()).isFalse();
    }

    @Test
    @DisplayName("공고 하나가 실패해도 나머지는 계속 받아온다")
    void keepsGoingAfterFailure() {
        CrawlSpec spec = jsonDetailSpec();

        server.expect(requestTo("https://example.com/detail/1")).andRespond(withServerError());
        server.expect(requestTo("https://example.com/detail/2"))
                .andRespond(withSuccess("""
                        {"content": "두 번째 공고 본문"}
                        """, MediaType.APPLICATION_JSON));

        List<CrawlRecord> records = fetcher.fetchAll(
                List.of(record(Map.of("job_id", "1")), record(Map.of("job_id", "2"))), spec, 0);

        server.verify();
        assertThat(records.getFirst().hasDetailError()).isTrue();
        assertThat(records.getFirst().detailError()).contains("500");
        assertThat(records.get(1).text(CrawlRecord.DESCRIPTION)).isEqualTo("두 번째 공고 본문");
    }

    @Test
    @DisplayName("건수를 제한하면 그만큼만 상세를 받는다")
    void honoursLimit() {
        CrawlSpec spec = jsonDetailSpec();

        server.expect(requestTo("https://example.com/detail/1"))
                .andRespond(withSuccess("""
                        {"content": "첫 번째 공고 본문"}
                        """, MediaType.APPLICATION_JSON));

        List<CrawlRecord> records = fetcher.fetchAll(
                List.of(record(Map.of("job_id", "1")), record(Map.of("job_id", "2"))), spec, 1);

        server.verify();
        assertThat(records.getFirst().text(CrawlRecord.DESCRIPTION)).isEqualTo("첫 번째 공고 본문");
        assertThat(records.get(1).value(CrawlRecord.DESCRIPTION)).isNull();
    }

    private CrawlSpec jsonDetailSpec() {
        return CrawlSpecFixtures.spec("""
                company: example
                source_type: json
                list: {url: https://example.com/api, response_path: list}
                fields: {job_id: id}
                detail:
                  enabled: true
                  source_type: json
                  url_template: https://example.com/detail/{job_id}
                  fields: {description: content}
                """);
    }

    private CrawlRecord record(Map<String, Object> values) {
        return new CrawlRecord(values, Map.of());
    }
}
