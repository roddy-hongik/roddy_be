package com.roddy.global.crawler.spec;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 수집 명세의 필드 경로. 한 필드가 여러 경로를 가질 수 있다.
 *
 * <pre>
 * title: jobOfferTitle                                  → 경로 1개
 * description: [introduction, workContentDesc, qualification]   → 경로 3개 (본문이 쪼개져 있는 회사)
 * </pre>
 */
@JsonDeserialize(using = FieldPath.Deserializer.class)
public record FieldPath(List<String> paths) {

    public FieldPath {
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("필드 경로가 비어 있습니다.");
        }
        paths = List.copyOf(paths);
    }

    public static FieldPath of(String... paths) {
        return new FieldPath(List.of(paths));
    }

    /** 경로가 여러 개면 각 경로를 해석해 줄바꿈으로 이어붙인다. */
    public boolean isMulti() {
        return paths.size() > 1;
    }

    public String single() {
        return paths.getFirst();
    }

    static class Deserializer extends JsonDeserializer<FieldPath> {

        @Override
        public FieldPath deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode node = parser.readValueAsTree();
            if (node.isArray()) {
                List<String> paths = new ArrayList<>();
                node.forEach(item -> paths.add(item.asText()));
                return new FieldPath(paths);
            }
            return new FieldPath(List.of(node.asText()));
        }
    }
}
