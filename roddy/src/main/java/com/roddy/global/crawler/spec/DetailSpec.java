package com.roddy.global.crawler.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

/**
 * 공고 상세 페이지 수집 설정.
 *
 * <p>목록 응답에 본문을 주는 회사는 얼마 없어서, 대부분은 공고마다 상세 페이지를 한 번 더 받아야
 * 본문이 채워진다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DetailSpec(
        boolean enabled,
        /** 상세 응답의 형태. 없으면 HTML 로 본다. */
        SourceType sourceType,
        /** 레코드 필드로 상세 주소를 조립할 때. urlFrom 보다 우선한다. */
        String urlTemplate,
        /** 상세 주소가 이미 레코드에 있을 때 그 필드명. 기본값은 apply_url. */
        String urlFrom,
        String scriptId,
        SelectSpec select,
        /** 결과 컬럼 → 상세 응답의 경로. json / embedded_json 에서 쓴다. */
        Map<String, FieldPath> fields,
        /** 본문 전체가 담긴 요소. html 에서 쓴다. */
        String bodySelector,
        /** 제목이 붙은 섹션 박스가 반복되는 요소. */
        String sectionBox,
        /** 섹션 박스 안에서 제목에 해당하는 요소. */
        String sectionTitle
) {

    private static final String DEFAULT_URL_FROM = "apply_url";
    private static final String DEFAULT_SCRIPT_ID = "__NEXT_DATA__";

    public DetailSpec {
        sourceType = sourceType == null ? SourceType.HTML : sourceType;
        urlFrom = urlFrom == null ? DEFAULT_URL_FROM : urlFrom;
        scriptId = scriptId == null ? DEFAULT_SCRIPT_ID : scriptId;
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
}
