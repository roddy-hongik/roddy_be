package com.roddy.global.crawler.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 공고 목록을 어디서 어떻게 받아오는지. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ListSpec(
        String url,
        String method,
        Map<String, Object> params,
        Map<String, String> headers,
        Map<String, Object> body,
        /** 응답에서 공고 배열이 있는 위치. */
        String responsePath,
        /** 배열의 각 항목에서 한 단계 더 들어가야 할 때 (토스의 primary_job 등). */
        String recordPath,
        /** {@link SourceType#EMBEDDED_JSON} 에서 JSON 이 박혀 있는 script 태그 id. */
        String scriptId,
        /** 배열에서 조건에 맞는 항목 하나를 골라야 할 때. 없으면 responsePath 로 바로 찾는다. */
        SelectSpec select
) {

    private static final String DEFAULT_SCRIPT_ID = "__NEXT_DATA__";

    public ListSpec {
        params = immutableCopy(params);
        headers = immutableCopy(headers);
        // body 는 null(본문 없음)과 빈 객체를 구분해야 하므로 null 을 유지한다.
        body = body == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(body));
        method = method != null
                ? method.trim().toUpperCase(Locale.ROOT)
                : (body != null ? "POST" : "GET");
        scriptId = scriptId == null ? DEFAULT_SCRIPT_ID : scriptId;
    }

    /** 값이 없는 항목은 빼고 복사한다. 쿼리 파라미터에 null 을 실어 보내지 않기 위함. */
    private static <T> Map<String, T> immutableCopy(Map<String, T> source) {
        if (source == null) {
            return Map.of();
        }
        Map<String, T> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (value != null) {
                copy.put(key, value);
            }
        });
        return Collections.unmodifiableMap(copy);
    }
}
