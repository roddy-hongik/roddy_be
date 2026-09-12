package com.roddy.global.crawler.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddedJsonExtractorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("script 태그에 박힌 JSON 을 꺼낸다")
    void extractsEmbeddedJson() {
        String html = """
                <html><head><title>채용</title></head>
                <body>
                <script id="__NEXT_DATA__" type="application/json">{"props":{"count":2}}</script>
                </body></html>
                """;

        JsonNode data = EmbeddedJsonExtractor.extract(html, "__NEXT_DATA__", MAPPER);

        assertThat(data).isNotNull();
        assertThat(data.get("props").get("count").intValue()).isEqualTo(2);
    }

    @Test
    @DisplayName("여러 줄에 걸친 JSON 도 꺼낸다")
    void extractsMultilineJson() {
        String html = """
                <script id="__NEXT_DATA__">
                {
                  "props": {"count": 1}
                }
                </script>
                """;

        JsonNode data = EmbeddedJsonExtractor.extract(html, "__NEXT_DATA__", MAPPER);

        assertThat(data.get("props").get("count").intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("해당 script 가 없거나 JSON 이 아니면 null 을 돌려준다")
    void returnsNullWhenNotExtractable() {
        assertThat(EmbeddedJsonExtractor.extract("<html></html>", "__NEXT_DATA__", MAPPER)).isNull();
        assertThat(EmbeddedJsonExtractor.extract(
                "<script id=\"__NEXT_DATA__\">not json</script>", "__NEXT_DATA__", MAPPER)).isNull();
        assertThat(EmbeddedJsonExtractor.extract(null, "__NEXT_DATA__", MAPPER)).isNull();
    }
}
