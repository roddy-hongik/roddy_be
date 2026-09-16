package com.roddy.global.crawler.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;

/**
 * 수집 명세의 점 표기법 경로를 해석한다.
 *
 * <pre>
 * a.b            → a 의 b
 * a[].b          → a 배열의 각 항목에서 b 를 꺼낸 배열
 * </pre>
 *
 * <p>경로 중간이 없거나 객체가 아니면 null 을 돌려준다. 수집 대상 사이트는 필드를 자주 빼먹기 때문에
 * 예외를 던지는 대신 "값 없음"으로 흘려보낸다.
 */
public final class JsonPathResolver {

    private static final String ARRAY_SUFFIX = "[]";

    private JsonPathResolver() {
    }

    public static JsonNode resolve(JsonNode node, String path) {
        if (node == null || node.isMissingNode() || path == null || path.isEmpty()) {
            return node;
        }

        int dot = path.indexOf('.');
        String segment = dot < 0 ? path : path.substring(0, dot);
        String rest = dot < 0 ? "" : path.substring(dot + 1);

        if (segment.endsWith(ARRAY_SUFFIX)) {
            return resolveArray(node, segment.substring(0, segment.length() - ARRAY_SUFFIX.length()), rest);
        }

        JsonNode next = node.isObject() ? node.get(segment) : null;
        return resolve(next, rest);
    }

    private static JsonNode resolveArray(JsonNode node, String key, String rest) {
        JsonNode array = node.isObject() ? node.get(key) : null;
        if (array == null || !array.isArray()) {
            return null;
        }

        ArrayNode mapped = JsonNodeFactory.instance.arrayNode();
        for (JsonNode item : array) {
            JsonNode resolved = resolve(item, rest);
            mapped.add(resolved == null ? NullNode.getInstance() : resolved);
        }
        return mapped;
    }
}
