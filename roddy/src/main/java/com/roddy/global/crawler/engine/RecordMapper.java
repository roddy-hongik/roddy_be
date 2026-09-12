package com.roddy.global.crawler.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.spec.ApplyUrlSpec;
import com.roddy.global.crawler.spec.CrawlSpec;
import com.roddy.global.crawler.spec.MetadataExtractionSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/** 수집 원본 JSON 한 건을 명세에 따라 {@link CrawlRecord} 로 옮긴다. */
public final class RecordMapper {

    private RecordMapper() {
    }

    public static CrawlRecord map(JsonNode raw, CrawlSpec spec) {
        Map<String, Object> values = new LinkedHashMap<>();
        spec.fields().forEach((column, path) -> values.put(column, JsonValues.resolveField(raw, path)));
        applyMetadataExtraction(values, raw, spec.metadataExtraction());

        Map<String, Object> extra = new LinkedHashMap<>();
        spec.extra().forEach((key, path) -> extra.put(key, JsonValues.resolveField(raw, path)));

        applyUrlTemplate(values, spec.applyUrl());
        applyNullValues(values, spec.nullValues());

        return new CrawlRecord(values, extra);
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
                byName.put(name.asText(), JsonValues.toPlain(item.get(spec.valueFrom())));
            }
        }

        spec.mappings().forEach((column, name) -> {
            Object value = byName.get(name);
            if (value != null && !String.valueOf(value).isEmpty()) {
                values.put(column, value);
            }
        });
    }

    private static void applyUrlTemplate(Map<String, Object> values, ApplyUrlSpec applyUrl) {
        if (applyUrl == null) {
            return;
        }
        String url = UrlTemplate.fill(applyUrl.template(), values);
        if (url != null) {
            values.put(CrawlRecord.APPLY_URL, url);
        }
    }

    private static void applyNullValues(Map<String, Object> values, Map<String, String> nullValues) {
        nullValues.forEach((field, marker) -> {
            Object value = values.get(field);
            if (value != null && String.valueOf(value).contains(marker)) {
                values.put(field, null);
            }
        });
    }
}
