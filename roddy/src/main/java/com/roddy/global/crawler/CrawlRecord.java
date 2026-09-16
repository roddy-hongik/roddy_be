package com.roddy.global.crawler;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 수집 엔진이 회사별 응답을 정규화해 내놓는 공고 한 건.
 *
 * <p>값은 원본 JSON 의 모양을 그대로 유지한다(문자열 / 숫자 / 불리언 / 리스트 / 맵).
 * 도메인 타입으로의 변환은 적재 단계에서 한다.
 *
 * @param detailError 상세 페이지 수집이 실패한 이유. 공고 하나가 실패해도 나머지 수집은 계속한다.
 */
public record CrawlRecord(Map<String, Object> values, Map<String, Object> extra, String detailError) {

    public static final String JOB_ID = "job_id";
    public static final String TITLE = "title";
    public static final String APPLY_URL = "apply_url";
    public static final String DESCRIPTION = "description";
    public static final String SECTIONS = "sections";

    public CrawlRecord {
        // 해석되지 않은 필드는 null 로 남으므로 null 값을 허용하는 맵을 쓴다.
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        extra = Collections.unmodifiableMap(new LinkedHashMap<>(extra));
    }

    public CrawlRecord(Map<String, Object> values, Map<String, Object> extra) {
        this(values, extra, null);
    }

    public Object value(String key) {
        return values.get(key);
    }

    /**
     * 값을 문자열로 본다. 리스트는 빈 값을 빼고 쉼표로 잇는다
     * (근무지처럼 {@code jobLocations[].name} 으로 여러 개가 오는 필드).
     */
    public String text(String key) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> collection) {
            String joined = collection.stream()
                    .filter(item -> item != null && !String.valueOf(item).isBlank())
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
            return joined.isEmpty() ? null : joined;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    /**
     * 필수 필드 점검용. null / 빈 문자열 / 빈 리스트를 비어 있다고 본다.
     *
     * <p>파이썬 구현은 falsy 검사라 {@code false} 와 {@code 0} 도 비었다고 봤지만,
     * 필수 필드는 항상 식별자나 제목이라 여기서는 값이 있는 것으로 취급한다.
     */
    public boolean isBlank(String key) {
        return text(key) == null;
    }

    /** 상세 수집으로 알아낸 값을 얹은 새 레코드. */
    public CrawlRecord merge(Map<String, Object> overrides) {
        if (overrides.isEmpty()) {
            return this;
        }
        Map<String, Object> merged = new LinkedHashMap<>(values);
        merged.putAll(overrides);
        return new CrawlRecord(merged, extra, detailError);
    }

    public CrawlRecord withDetailError(String detailError) {
        return new CrawlRecord(values, extra, detailError);
    }

    public boolean hasDetailError() {
        return detailError != null;
    }
}
