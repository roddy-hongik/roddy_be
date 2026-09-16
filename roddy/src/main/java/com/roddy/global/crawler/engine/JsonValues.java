package com.roddy.global.crawler.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.roddy.global.crawler.spec.FieldPath;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 수집 원본 JSON 값을 평범한 자바 값으로 다루기 위한 도구. */
public final class JsonValues {

    private static final String MULTI_PATH_DELIMITER = "\n\n";
    private static final String SECTION_TITLE_KEY = "title";
    private static final String SECTION_CONTENTS_KEY = "contents";

    private JsonValues() {
    }

    /** 명세의 필드 경로를 해석한다. 경로가 여러 개면 빈 조각을 빼고 이어붙인다. */
    public static Object resolveField(JsonNode raw, FieldPath path) {
        if (!path.isMulti()) {
            return toPlain(JsonPathResolver.resolve(raw, path.single()));
        }

        List<String> parts = path.paths().stream()
                .map(each -> toPlain(JsonPathResolver.resolve(raw, each)))
                .filter(value -> value != null && !String.valueOf(value).isBlank())
                .map(value -> String.valueOf(value).trim())
                .toList();
        return parts.isEmpty() ? null : String.join(MULTI_PATH_DELIMITER, parts);
    }

    /** JsonNode 를 평범한 자바 값으로 바꾼다. 적재 단계가 Jackson 타입을 알 필요는 없다. */
    public static Object toPlain(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isArray()) {
            List<Object> items = new ArrayList<>();
            node.forEach(item -> items.add(toPlain(item)));
            return items;
        }
        if (node.isObject()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            node.properties().forEach(entry -> fields.put(entry.getKey(), toPlain(entry.getValue())));
            return fields;
        }
        return node.asText();
    }

    /**
     * 본문이 {@code [{title, contents}, ...]} 형태로 오는 회사를 읽을 수 있는 글로 편다.
     *
     * <p>그 형태가 아니면 값을 그대로 돌려준다.
     */
    public static Object flatten(Object value) {
        if (!(value instanceof List<?> items) || items.isEmpty() || !(items.getFirst() instanceof Map<?, ?>)) {
            return value;
        }

        List<String> parts = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> section)) {
                continue;
            }
            String title = asText(section.get(SECTION_TITLE_KEY));
            String contents = joinContents(section.get(SECTION_CONTENTS_KEY));
            String merged = title.isEmpty() ? contents.strip() : (title + "\n" + contents).strip();
            if (!merged.isEmpty()) {
                parts.add(merged);
            }
        }
        return parts.isEmpty() ? value : String.join(MULTI_PATH_DELIMITER, parts);
    }

    private static String joinContents(Object contents) {
        if (contents instanceof List<?> lines) {
            return lines.stream()
                    .filter(line -> line != null && !String.valueOf(line).isBlank())
                    .map(String::valueOf)
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
        }
        return asText(contents);
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
