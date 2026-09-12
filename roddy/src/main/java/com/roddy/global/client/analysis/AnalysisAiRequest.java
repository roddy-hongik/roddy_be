package com.roddy.global.client.analysis;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * AI 서버에 넘길 분석 재료.
 *
 * @param githubToken       사용자 깃허브 토큰. 없으면 공개 API 한도(시간당 60회)에 묶인다.
 * @param portfolioUrl      백엔드가 만든 presigned 주소. AI 서버는 S3 자격증명을 갖지 않는다.
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
        String experienceYears
) {
}
