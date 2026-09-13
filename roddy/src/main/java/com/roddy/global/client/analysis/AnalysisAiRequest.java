package com.roddy.global.client.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * AI 서버에 넘길 분석 재료.
 *
 * @param githubToken       사용자 깃허브 토큰. 없으면 공개 API 한도(시간당 60회)에 묶인다.
 * @param portfolioUrl      백엔드가 만든 presigned 주소. AI 서버는 S3 자격증명을 갖지 않는다.
 * @param categories        평가 축. 축을 정하지 않은 직무면 비어 있고, 그러면 축별 점수 없이 분석한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnalysisAiRequest(
        Long userId,
        String githubUrl,
        String githubToken,
        String portfolioUrl,
        String portfolioFileName,
        String desiredJob,
        String experienceYears,
        List<Category> categories
) {

    public AnalysisAiRequest {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }

    /** 평가 축 하나. AI 서버는 description 을 채점 기준으로 쓴다. */
    public record Category(String code, String name, String description) {
    }
}
