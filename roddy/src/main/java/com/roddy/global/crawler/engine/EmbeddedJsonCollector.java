package com.roddy.global.crawler.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.spec.CrawlSpec;
import com.roddy.global.crawler.spec.ListSpec;
import com.roddy.global.crawler.spec.SelectSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * SSG 페이지에 박힌 JSON 에서 공고 목록을 꺼내 매핑한다.
 *
 * <p>페이지 하나에 전체가 들어 있어 페이지네이션이 없다.
 */
public class EmbeddedJsonCollector {

    private final CrawlHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EmbeddedJsonCollector(CrawlHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<CrawlRecord> collect(CrawlSpec spec) {
        ListSpec list = spec.list();
        String html = httpClient.fetch(list.url(), list.method(), list.params(), list.headers(), list.body());

        JsonNode data = EmbeddedJsonExtractor.extract(html, list.scriptId(), objectMapper);
        if (data == null) {
            return List.of();
        }

        JsonNode array = selectArray(data, list);
        if (array == null || !array.isArray()) {
            return List.of();
        }

        List<CrawlRecord> records = new ArrayList<>();
        array.forEach(raw -> records.add(RecordMapper.map(raw, spec)));
        return records;
    }

    private JsonNode selectArray(JsonNode data, ListSpec list) {
        SelectSpec select = list.select();
        if (select == null) {
            return JsonPathResolver.resolve(data, list.responsePath());
        }

        JsonNode candidates = JsonPathResolver.resolve(data, select.array());
        if (candidates == null || !candidates.isArray()) {
            return null;
        }

        for (JsonNode candidate : candidates) {
            if (select.matches(JsonPathResolver.resolve(candidate, select.matchField()))) {
                return JsonPathResolver.resolve(candidate, select.take());
            }
        }
        return null;
    }
}
