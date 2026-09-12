package com.roddy.global.crawler.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.spec.CrawlSpec;
import com.roddy.global.crawler.spec.DetailSpec;
import com.roddy.global.crawler.spec.SelectSpec;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 공고마다 상세 페이지를 한 번 더 받아 본문을 채운다.
 *
 * <p>공고 하나가 실패해도 나머지 수집은 계속한다. 실패한 공고에는 이유만 남긴다.
 */
public class DetailFetcher {

    private static final String GET = "GET";

    private final CrawlHttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 상세는 공고 수만큼 요청하므로 회사 사이트에 부담을 주지 않도록 사이를 띄운다. */
    private final Duration delayBetweenRequests;

    public DetailFetcher(CrawlHttpClient httpClient, Duration delayBetweenRequests) {
        this.httpClient = httpClient;
        this.delayBetweenRequests = delayBetweenRequests;
    }

    /**
     * @param limit 상세를 받아올 공고 수. 0 이하면 전부 받는다. 명세를 점검할 때 몇 건만 받아보는 용도.
     */
    public List<CrawlRecord> fetchAll(List<CrawlRecord> records, CrawlSpec spec, int limit) {
        List<CrawlRecord> fetched = new ArrayList<>(records.size());
        int attempted = 0;

        for (CrawlRecord record : records) {
            if (limit > 0 && attempted >= limit) {
                fetched.add(record);
                continue;
            }
            if (attempted > 0) {
                pause();
            }
            fetched.add(fetch(record, spec));
            attempted++;
        }
        return fetched;
    }

    private CrawlRecord fetch(CrawlRecord record, CrawlSpec spec) {
        DetailSpec detail = spec.detail();
        String url = resolveUrl(record, detail);
        if (url == null || url.isBlank()) {
            return record;
        }

        try {
            String body = httpClient.fetch(url, GET, Map.of(), spec.list().headers(), null);
            return switch (detail.sourceType()) {
                case JSON -> applyJson(record, body, detail);
                case EMBEDDED_JSON -> applyEmbeddedJson(record, body, detail);
                case HTML -> applyHtml(record, body, url, detail);
            };
        } catch (Exception e) {
            return record.withDetailError("%s: %s".formatted(e.getClass().getSimpleName(), e.getMessage()));
        }
    }

    /** 주소 조립이 우선이고, 없으면 레코드가 이미 들고 있는 주소를 쓴다. */
    private String resolveUrl(CrawlRecord record, DetailSpec detail) {
        if (detail.urlTemplate() != null) {
            return UrlTemplate.fill(detail.urlTemplate(), record.values());
        }
        return record.text(detail.urlFrom());
    }

    private CrawlRecord applyJson(CrawlRecord record, String body, DetailSpec detail) {
        return record.merge(readFields(readTree(body), detail));
    }

    private CrawlRecord applyEmbeddedJson(CrawlRecord record, String body, DetailSpec detail) {
        JsonNode data = EmbeddedJsonExtractor.extract(body, detail.scriptId(), objectMapper);
        if (data == null) {
            return record;
        }
        return record.merge(readFields(selectBase(data, detail.select()), detail));
    }

    private CrawlRecord applyHtml(CrawlRecord record, String body, String url, DetailSpec detail) {
        Document document = Jsoup.parse(body, url);
        Map<String, Object> values = new LinkedHashMap<>();

        if (detail.bodySelector() != null) {
            String description = HtmlTextExtractor.selectText(document, detail.bodySelector());
            if (description != null && !description.isBlank()) {
                values.put(CrawlRecord.DESCRIPTION, description);
            }
        }
        if (detail.sectionBox() != null) {
            Map<String, String> sections =
                    HtmlTextExtractor.sections(document, detail.sectionBox(), detail.sectionTitle());
            if (!sections.isEmpty()) {
                values.put(CrawlRecord.SECTIONS, sections);
            }
        }
        return record.merge(values);
    }

    /** select 규칙이 있으면 조건에 맞는 항목을 기준점으로 삼는다 (React Query 캐시 등). */
    private JsonNode selectBase(JsonNode data, SelectSpec select) {
        if (select == null) {
            return data;
        }

        JsonNode candidates = JsonPathResolver.resolve(data, select.array());
        if (candidates == null || !candidates.isArray()) {
            return data;
        }
        for (JsonNode candidate : candidates) {
            if (select.matches(JsonPathResolver.resolve(candidate, select.matchField()))) {
                return JsonPathResolver.resolve(candidate, select.take());
            }
        }
        return data;
    }

    private Map<String, Object> readFields(JsonNode base, DetailSpec detail) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (base == null) {
            return values;
        }

        detail.fields().forEach((column, path) -> {
            Object value = JsonValues.flatten(JsonValues.resolveField(base, path));
            if (value != null && !String.valueOf(value).isEmpty()) {
                values.put(column, value);
            }
        });
        return values;
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("상세 응답이 JSON 이 아닙니다.", e);
        }
    }

    private void pause() {
        if (delayBetweenRequests.isZero() || delayBetweenRequests.isNegative()) {
            return;
        }
        try {
            Thread.sleep(delayBetweenRequests);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("상세 수집이 중단되었습니다.", e);
        }
    }
}
