package com.roddy.global.crawler.engine;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlTextExtractorTest {

    @Test
    @DisplayName("문단 구분을 줄바꿈으로 살려 본문을 뽑는다")
    void keepsLineBreaks() {
        Document document = Jsoup.parse("""
                <div class="content">
                  <h2>주요 업무</h2>
                  <ul><li>서버 개발</li><li>API 설계</li></ul>
                </div>
                """);

        assertThat(HtmlTextExtractor.selectText(document, ".content"))
                .isEqualTo("주요 업무\n서버 개발\nAPI 설계");
    }

    @Test
    @DisplayName("스크립트와 스타일은 본문에 넣지 않는다")
    void skipsScriptAndStyle() {
        Document document = Jsoup.parse("""
                <body>
                  <script>window.__DATA__ = {"a":1};</script>
                  <style>.a { color: red; }</style>
                  <p>백엔드 개발자를 찾습니다.</p>
                </body>
                """);

        assertThat(HtmlTextExtractor.selectText(document, "body"))
                .isEqualTo("백엔드 개발자를 찾습니다.");
    }

    @Test
    @DisplayName("맞는 요소가 없으면 null 을 돌려준다")
    void returnsNullWhenSelectorMisses() {
        Document document = Jsoup.parse("<div class='content'>본문</div>");

        assertThat(HtmlTextExtractor.selectText(document, ".missing")).isNull();
    }

    @Test
    @DisplayName("제목이 붙은 섹션을 제목 → 본문으로 모은다")
    void collectsSections() {
        Document document = Jsoup.parse("""
                <div class="detail_wrap">
                  <div class="detail_box">
                    <h4 class="detail_title">Who We Are</h4>
                    <p class="detail_text">네이버 서치 팀입니다.</p>
                  </div>
                  <div class="detail_box">
                    <h4 class="detail_title">Required Skills</h4>
                    <p class="detail_text">Java</p>
                    <p class="detail_text">Kotlin</p>
                  </div>
                  <div class="detail_box">
                    <p class="detail_text">제목 없는 박스는 건너뛴다</p>
                  </div>
                </div>
                """);

        Map<String, String> sections =
                HtmlTextExtractor.sections(document, "div.detail_box", "h4.detail_title");

        assertThat(sections).hasSize(2);
        assertThat(sections.get("Who We Are")).isEqualTo("네이버 서치 팀입니다.");
        assertThat(sections.get("Required Skills")).isEqualTo("Java\nKotlin");
    }

    @Test
    @DisplayName("섹션 설정이 없으면 빈 결과를 돌려준다")
    void returnsEmptySectionsWithoutSelectors() {
        Document document = Jsoup.parse("<div class='detail_box'>본문</div>");

        assertThat(HtmlTextExtractor.sections(document, "div.detail_box", null)).isEmpty();
        assertThat(HtmlTextExtractor.sections(document, null, "h4")).isEmpty();
    }
}
