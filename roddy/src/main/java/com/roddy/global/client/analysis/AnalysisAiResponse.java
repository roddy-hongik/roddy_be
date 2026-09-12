package com.roddy.global.client.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/** AI 서버가 만들어 준 역량 리포트. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnalysisAiResponse(
        String title,
        int totalScore,
        String summary,
        String githubAnalysis,
        String portfolioAnalysis,
        List<AnalyzedStack> stacks,
        Sources sources
) {

    public AnalysisAiResponse {
        stacks = stacks == null ? List.of() : List.copyOf(stacks);
    }

    /**
     * 분석이 찾아낸 기술 하나.
     *
     * @param level 백엔드의 StackLevel 과 같은 값 (BEGINNER / INTERMEDIATE / ADVANCED / EXPERT)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AnalyzedStack(String name, int score, String level, String description) {
    }

    /** 무엇을 근거로 분석했는지. 결과가 빈약할 때 원인을 짚는 데 쓴다. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Sources(int repositoryCount, boolean portfolioIncluded, List<String> warnings) {

        public Sources {
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
    }
}
