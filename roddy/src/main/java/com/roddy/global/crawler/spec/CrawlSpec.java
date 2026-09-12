package com.roddy.global.crawler.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/**
 * 회사 한 곳의 채용공고 수집 명세.
 *
 * <p>회사별 응답 형태를 흡수하는 지식은 전부 이 명세(YAML)에 있고, 엔진은 명세를 해석하기만 한다.
 * 사이트가 개편되면 코드가 아니라 명세를 고친다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CrawlSpec(
        /** 회사 식별 코드 (예: kakao). */
        String company,
        /** 표시용 회사명 (예: 카카오). */
        String companyNameKo,
        /** 사람이 보는 채용 페이지 주소. 수집에는 쓰지 않고 명세를 되짚을 때 쓴다. */
        String entryUrl,
        String crawler,
        SourceType sourceType,
        ListSpec list,
        PaginationSpec pagination,
        /** 수집 결과 점검 시 비어 있으면 안 되는 필드. */
        List<String> required,
        Map<String, FieldPath> fields,
        /** 정규화 대상은 아니지만 원본으로 남겨둘 값. */
        Map<String, FieldPath> extra,
        ApplyUrlSpec applyUrl,
        FilterSpec filter,
        /** 값에 이 문자열이 들어 있으면 null 로 본다 (예: 마감일 9999년 표기). */
        Map<String, String> nullValues,
        MetadataExtractionSpec metadataExtraction,
        DetailSpec detail
) {

    public static final List<String> DEFAULT_REQUIRED = List.of("job_id", "title", "updated_at");

    public CrawlSpec {
        required = (required == null || required.isEmpty()) ? DEFAULT_REQUIRED : List.copyOf(required);
        fields = fields == null ? Map.of() : Map.copyOf(fields);
        extra = extra == null ? Map.of() : Map.copyOf(extra);
        nullValues = nullValues == null ? Map.of() : Map.copyOf(nullValues);
        pagination = pagination == null ? PaginationSpec.none() : pagination;
    }

    public boolean hasDetail() {
        return detail != null && detail.enabled();
    }
}
