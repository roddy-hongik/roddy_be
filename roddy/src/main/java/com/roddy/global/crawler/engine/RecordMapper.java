package com.roddy.global.crawler.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.spec.ApplyUrlSpec;
import com.roddy.global.crawler.spec.CrawlSpec;
import com.roddy.global.crawler.spec.FieldPath;
import com.roddy.global.crawler.spec.MetadataExtractionSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 수집 원본 JSON 한 건을 명세에 따라 {@link CrawlRecord} 로 옮긴다. */
public final class RecordMapper {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static final String MULTI_PATH_DELIMITER = "\n\n";

    private RecordMapper() {
    }

    public static CrawlRecord map(JsonNode raw, CrawlSpec spec) {
        Map<String, Object> values = new LinkedHashMap<>();
        spec.fields().forEach((column, path) -> values.put(column, resolveField(raw, path)));
        applyMetadataExtraction(values, raw, spec.metadataExtraction());

        Map<String, Object> extra = new LinkedHashMap<>();
        spec.extra().forEach((key, path) -> extra.put(key, resolveField(raw, path)));

        applyUrlTemplate(values, spec.applyUrl());
        applyNullValues(values, spec.nullValues());

        return new CrawlRecord(values, extra);
    }

    private static Object resolveField(JsonNode raw, FieldPath path) {
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

    private static void applyMetadataExtraction(Map<String, Object> values, JsonNode raw,
                                                MetadataExtractionSpec spec) {
        if (spec == null) {
            return;
        }
        JsonNode array = JsonPathResolver.resolve(raw, spec.sourceField());
        if (array == null || !array.isArray()) {
            return;
        }

        Map<String, Object> byName = new LinkedHashMap<>();
        for (JsonNode item : array) {
            if (!item.isObject()) {
                continue;
            }
            JsonNode name = item.get(spec.matchBy());
            if (name != null && !name.isNull()) {
                byName.put(name.asText(), toPlain(item.get(spec.valueFrom())));
            }
        }

        spec.mappings().forEach((column, name) -> {
            Object value = byName.get(name);
            if (value != null && !String.valueOf(value).isEmpty()) {
                values.put(column, value);
            }
        });
    }

    /**
     * 템플릿의 중괄호 자리를 레코드 필드 값으로 채운다.
     *
     * <p>채울 값이 없으면 조립을 포기한다. 파이썬 구현은 값이 None 이어도 "None" 이라는 글자를 끼워
     * 넣었지만, 그렇게 만든 주소는 어차피 쓸 수 없어 아예 두지 않는 편이 낫다.
     */
    private static void applyUrlTemplate(Map<String, Object> values, ApplyUrlSpec applyUrl) {
        if (applyUrl == null || applyUrl.template() == null) {
            return;
        }

        String template = applyUrl.template();
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder url = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            Object value = values.get(matcher.group(1));
            if (value == null) {
                return;
            }
            url.append(template, cursor, matcher.start()).append(value);
            cursor = matcher.end();
        }
        url.append(template, cursor, template.length());
        values.put(CrawlRecord.APPLY_URL, url.toString());
    }

    private static void applyNullValues(Map<String, Object> values, Map<String, String> nullValues) {
        nullValues.forEach((field, marker) -> {
            Object value = values.get(field);
            if (value != null && String.valueOf(value).contains(marker)) {
                values.put(field, null);
            }
        });
    }

    /** JsonNode 를 평범한 자바 값으로 바꾼다. 적재 단계가 Jackson 타입을 알 필요는 없다. */
    private static Object toPlain(JsonNode node) {
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
}
