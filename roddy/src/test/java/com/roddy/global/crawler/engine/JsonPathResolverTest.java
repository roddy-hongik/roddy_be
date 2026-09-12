package com.roddy.global.crawler.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonPathResolverTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("점 표기법으로 중첩된 값을 꺼낸다")
    void resolvesNestedValue() {
        JsonNode root = read("""
                {"data": {"list": [{"id": 1}], "total": 3}}
                """);

        assertThat(JsonPathResolver.resolve(root, "data.total").intValue()).isEqualTo(3);
        assertThat(JsonPathResolver.resolve(root, "data.list").isArray()).isTrue();
    }

    @Test
    @DisplayName("배열 표기법으로 각 항목의 하위 필드를 모은다")
    void resolvesArrayField() {
        JsonNode root = read("""
                {"jobLocations": [{"name": "판교"}, {"name": "제주"}]}
                """);

        JsonNode resolved = JsonPathResolver.resolve(root, "jobLocations[].name");

        assertThat(resolved.isArray()).isTrue();
        assertThat(textValues(resolved)).containsExactly("판교", "제주");
    }

    @Test
    @DisplayName("배열 항목에 필드가 없으면 그 자리는 null 로 둔다")
    void keepsNullSlotForMissingField() {
        JsonNode root = read("""
                {"items": [{"name": "판교"}, {}]}
                """);

        JsonNode resolved = JsonPathResolver.resolve(root, "items[].name");

        assertThat(resolved.size()).isEqualTo(2);
        assertThat(resolved.get(1).isNull()).isTrue();
    }

    @Test
    @DisplayName("경로 중간이 없거나 배열이 아니면 null 을 돌려준다")
    void returnsNullForBrokenPath() {
        JsonNode root = read("""
                {"data": {"total": 3}}
                """);

        assertThat(JsonPathResolver.resolve(root, "data.missing.deeper")).isNull();
        assertThat(JsonPathResolver.resolve(root, "data.total.deeper")).isNull();
        assertThat(JsonPathResolver.resolve(root, "data[].name")).isNull();
    }

    @Test
    @DisplayName("빈 경로는 노드를 그대로 돌려준다")
    void returnsNodeForEmptyPath() {
        JsonNode root = read("""
                {"a": 1}
                """);

        assertThat(JsonPathResolver.resolve(root, "")).isSameAs(root);
        assertThat(JsonPathResolver.resolve(root, null)).isSameAs(root);
        assertThat(JsonPathResolver.resolve(null, "a")).isNull();
    }

    private List<String> textValues(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.textValue()));
        return values;
    }

    private JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
