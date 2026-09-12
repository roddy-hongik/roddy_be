package com.roddy.global.crawler.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 배열에서 조건에 맞는 항목 하나를 골라내는 규칙.
 *
 * <p>React Query 캐시처럼 {@code queries[]} 안에서 {@code queryKey} 가 특정 값인 항목의
 * {@code state.data} 를 꺼내야 하는 경우에 쓴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SelectSpec(
        String array,
        String matchField,
        JsonNode matchValue,
        List<String> matchPrefix,
        String take
) {

    public SelectSpec {
        matchPrefix = matchPrefix == null ? List.of() : List.copyOf(matchPrefix);
    }

    /**
     * {@code matchField} 로 해석한 값이 이 규칙에 맞는지 본다.
     * {@code matchValue} 는 완전 일치, {@code matchPrefix} 는 배열의 앞부분 일치로 판정한다.
     */
    public boolean matches(JsonNode key) {
        if (matchValue != null && !matchValue.isNull()) {
            return matchValue.equals(key);
        }
        if (matchPrefix.isEmpty() || key == null || !key.isArray() || key.size() < matchPrefix.size()) {
            return false;
        }
        for (int i = 0; i < matchPrefix.size(); i++) {
            if (!matchPrefix.get(i).equals(key.get(i).asText())) {
                return false;
            }
        }
        return true;
    }
}
