package com.roddy.global.crawler.spec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlSpecLoaderTest {

    private final CrawlSpecLoader loader = new CrawlSpecLoader();

    @Test
    @DisplayName("수집 명세 전체가 파싱되고 최소 형태를 갖춘다")
    void loadsEverySpec() {
        List<CrawlSpec> specs = loader.loadAll();

        assertThat(specs).isNotEmpty();
        assertThat(specs).allSatisfy(spec -> {
            assertThat(spec.company()).isNotBlank();
            assertThat(spec.sourceType()).isNotNull();
            assertThat(spec.list()).isNotNull();
            assertThat(spec.list().url()).startsWith("https://");
            assertThat(spec.fields()).containsKeys("job_id", "title");
            assertThat(spec.required()).isNotEmpty();
        });
    }

    @Test
    @DisplayName("명세 파일명이 곧 회사 코드다")
    void fileNameMatchesCompanyCode() {
        loader.loadAll().forEach(spec ->
                assertThat(loader.load(spec.company()).company()).isEqualTo(spec.company()));
    }

    @Test
    @DisplayName("자체 JSON API 명세를 그대로 읽는다")
    void readsJsonSpec() {
        CrawlSpec spec = loader.load("kakao");

        assertThat(spec.sourceType()).isEqualTo(SourceType.JSON);
        assertThat(spec.list().url()).isEqualTo("https://careers.kakao.com/public/api/job-list");
        assertThat(spec.list().method()).isEqualTo("GET");
        assertThat(spec.list().responsePath()).isEqualTo("jobList");
        assertThat(spec.pagination().type()).isEqualTo(PaginationSpec.Type.NONE);
        assertThat(spec.fields().get("job_id").single()).isEqualTo("realId");
        assertThat(spec.fields().get("description").paths())
                .containsExactly("introduction", "workContentDesc", "qualification");
        assertThat(spec.applyUrl().template()).isEqualTo("https://careers.kakao.com/jobs/{job_id}");
    }

    @Test
    @DisplayName("임베드 JSON 명세는 script id 와 select 규칙을 읽는다")
    void readsEmbeddedJsonSpec() {
        CrawlSpec spec = loader.load("kakaopay");

        assertThat(spec.sourceType()).isEqualTo(SourceType.EMBEDDED_JSON);
        assertThat(spec.list().scriptId()).isEqualTo("__NEXT_DATA__");
        assertThat(spec.list().select()).isNotNull();
        assertThat(spec.list().select().array()).isEqualTo("props.pageProps.dehydratedState.queries");
        assertThat(spec.list().select().matchField()).isEqualTo("queryKey");
        assertThat(spec.hasDetail()).isTrue();
    }

    @Test
    @DisplayName("POST 로 받는 명세는 헤더와 본문을 읽는다")
    void readsPostSpec() {
        CrawlSpec spec = loader.load("kakaobank");

        assertThat(spec.list().method()).isEqualTo("POST");
        assertThat(spec.list().headers()).containsEntry("Accept", "application/json");
        assertThat(spec.list().body()).containsEntry("pageSize", 100);
    }

    @Test
    @DisplayName("페이지네이션 명세를 읽는다")
    void readsPaginationSpec() {
        CrawlSpec pageNumber = loader.load("nhn");
        CrawlSpec offset = loader.load("naver");

        assertThat(pageNumber.pagination().type()).isEqualTo(PaginationSpec.Type.PAGE_NUMBER);
        assertThat(pageNumber.pagination().param()).isEqualTo("page");
        assertThat(pageNumber.pagination().startValue()).isZero();
        assertThat(pageNumber.nullValues()).containsEntry("deadline", "2999");

        assertThat(offset.pagination().type()).isEqualTo(PaginationSpec.Type.OFFSET);
        assertThat(offset.pagination().param()).isEqualTo("firstIndex");
    }

    @Test
    @DisplayName("메타데이터 매핑과 record_path 명세를 읽는다")
    void readsMetadataExtractionSpec() {
        CrawlSpec spec = loader.load("toss");

        assertThat(spec.list().recordPath()).isEqualTo("primary_job");
        assertThat(spec.metadataExtraction()).isNotNull();
        assertThat(spec.metadataExtraction().sourceField()).isEqualTo("metadata");
        assertThat(spec.metadataExtraction().matchBy()).isEqualTo("name");
        assertThat(spec.metadataExtraction().mappings()).containsKey("employment_type");
    }

    @Test
    @DisplayName("상세 수집 명세는 전부 주소를 정할 방법과 본문을 꺼낼 방법을 갖고 있다")
    void loadsEveryDetailSpec() {
        List<CrawlSpec> withDetail = loader.loadAll().stream().filter(CrawlSpec::hasDetail).toList();

        assertThat(withDetail).isNotEmpty();
        assertThat(withDetail).allSatisfy(spec -> {
            DetailSpec detail = spec.detail();
            assertThat(detail.sourceType()).isNotNull();

            // 상세 주소는 조립하거나, 레코드가 이미 들고 있어야 한다.
            boolean hasUrl = detail.urlTemplate() != null
                    || spec.fields().containsKey(detail.urlFrom())
                    || spec.applyUrl() != null;
            assertThat(hasUrl).isTrue();

            if (detail.sourceType() == SourceType.HTML) {
                assertThat(detail.bodySelector()).isNotBlank();
            } else {
                assertThat(detail.fields()).isNotEmpty();
            }
        });
    }

    @Test
    @DisplayName("HTML 상세 명세는 본문과 섹션 셀렉터를 읽는다")
    void readsHtmlDetailSpec() {
        DetailSpec detail = loader.load("naver").detail();

        assertThat(detail.sourceType()).isEqualTo(SourceType.HTML);
        assertThat(detail.urlFrom()).isEqualTo("apply_url");
        assertThat(detail.bodySelector()).isEqualTo("div.detail_wrap");
        assertThat(detail.sectionBox()).isEqualTo("div.detail_box");
        assertThat(detail.sectionTitle()).isEqualTo("h4.detail_title");
    }

    @Test
    @DisplayName("JSON 상세 명세는 조립할 주소와 꺼낼 경로를 읽는다")
    void readsJsonDetailSpec() {
        DetailSpec detail = loader.load("woowahan").detail();

        assertThat(detail.sourceType()).isEqualTo(SourceType.JSON);
        assertThat(detail.urlTemplate())
                .isEqualTo("https://career.woowahan.com/w1/recruits/{recruit_number}");
        assertThat(detail.fields().get("description").single()).isEqualTo("data.recruitContents");
    }

    @Test
    @DisplayName("인재풀 제외 필터 명세를 읽는다")
    void readsFilterSpec() {
        CrawlSpec spec = loader.load("musinsa");

        assertThat(spec.filter()).isNotNull();
        assertThat(spec.filter().field()).isEqualTo("title");
        assertThat(spec.filter().excludeContains()).contains("인재풀");
    }
}
