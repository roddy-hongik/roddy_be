package com.roddy.global.crawler.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * 매핑된 레코드를 거르는 규칙.
 *
 * <p>"인재풀", "Talent Pool" 처럼 공고가 아닌 항목이 목록에 섞여 들어오는 회사가 있어서 필요하다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FilterSpec(
        String field,
        /** 이 목록에 든 값만 남긴다. */
        List<String> in,
        /** 이 문자열 중 하나라도 들어 있으면 제외한다. 대소문자를 구분하지 않는다. */
        List<String> excludeContains
) {

    private static final String DEFAULT_FIELD = "title";

    public FilterSpec {
        field = (field == null || field.isBlank()) ? DEFAULT_FIELD : field;
        in = in == null ? null : List.copyOf(in);
        excludeContains = excludeContains == null ? List.of() : List.copyOf(excludeContains);
    }
}
