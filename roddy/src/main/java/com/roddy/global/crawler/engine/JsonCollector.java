package com.roddy.global.crawler.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.spec.CrawlSpec;
import com.roddy.global.crawler.spec.ListSpec;
import com.roddy.global.crawler.spec.PaginationSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** REST JSON API 로 공고 목록을 받아 매핑한다. */
public class JsonCollector {

    /** 명세가 잘못돼 종료 조건에 걸리지 않을 때 무한히 도는 것을 막는 상한. */
    private static final int MAX_REQUESTS = 500;

    private final CrawlHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonCollector(CrawlHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<CrawlRecord> collect(CrawlSpec spec) {
        ListSpec list = spec.list();
        PaginationSpec pagination = spec.pagination();

        List<CrawlRecord> records = new ArrayList<>();
        int cursor = pagination.startValue();

        for (int requests = 0; requests < MAX_REQUESTS; requests++) {
            Map<String, Object> params = new LinkedHashMap<>(list.params());
            Map<String, Object> body = list.body() == null ? null : new LinkedHashMap<>(list.body());
            // POST API 는 보통 본문으로 페이징하므로, 본문이 있으면 그쪽에 싣는다.
            Map<String, Object> pageTarget = body != null ? body : params;

            if (pagination.type() != PaginationSpec.Type.NONE) {
                pageTarget.put(pagination.param(), cursor);
            }

            JsonNode root = readTree(httpClient.fetch(list.url(), list.method(), params, list.headers(), body));
            List<JsonNode> batch = extractBatch(root, list);
            batch.forEach(raw -> records.add(RecordMapper.map(raw, spec)));

            if (pagination.type() == PaginationSpec.Type.NONE || batch.isEmpty()) {
                break;
            }
            if (pagination.type() == PaginationSpec.Type.PAGE_NUMBER) {
                Integer totalPages = readTotalPages(root, pagination.totalPagesPath());
                if (totalPages != null && cursor >= totalPages) {
                    break;
                }
                cursor++;
            } else {
                cursor += batch.size();
            }
        }
        return records;
    }

    private List<JsonNode> extractBatch(JsonNode root, ListSpec list) {
        JsonNode array = JsonPathResolver.resolve(root, list.responsePath());
        List<JsonNode> batch = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return batch;
        }

        for (JsonNode item : array) {
            JsonNode record = list.recordPath() == null
                    ? item
                    : JsonPathResolver.resolve(item, list.recordPath());
            if (record != null && !record.isNull()) {
                batch.add(record);
            }
        }
        return batch;
    }

    /** 전체 페이지 수는 숫자로 오기도 하고 문자열로 오기도 한다. */
    private Integer readTotalPages(JsonNode root, String path) {
        if (path == null) {
            return null;
        }
        JsonNode total = JsonPathResolver.resolve(root, path);
        if (total == null || total.isNull()) {
            return null;
        }
        if (total.isNumber()) {
            return total.intValue();
        }
        try {
            return Integer.parseInt(total.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("수집 응답이 JSON 이 아닙니다.", e);
        }
    }
}
