package com.roddy.global.crawler.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

/**
 * {@code [{name, value}, ...]} 형태의 메타데이터 배열에서 값을 꺼내 필드로 올리는 규칙.
 *
 * <p>Greenhouse 계열은 회사별 커스텀 필드를 이 형태로 내려주기 때문에, 이름으로 찾아 매핑해야 한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MetadataExtractionSpec(
        /** 메타데이터 배열의 위치. */
        String sourceField,
        /** 항목을 식별할 이름이 든 필드. */
        String matchBy,
        /** 값이 든 필드. */
        String valueFrom,
        /** 결과 컬럼명 → 찾을 이름. */
        Map<String, String> mappings
) {

    public MetadataExtractionSpec {
        mappings = mappings == null ? Map.of() : Map.copyOf(mappings);
    }
}
