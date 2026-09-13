package com.roddy.global.client.roadmap;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/** AI 서버는 snake_case 로 받는다. RestClient 는 Jackson 3 으로 직렬화하므로 Jackson 3 의 어노테이션을 쓴다. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RoadMapAiRequest(
        List<String> currentSkills,
        List<String> gapSkills,
        String targetJob,
        String targetCompany
) {
}
