package com.roddy.global.client.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * AI 서버가 만들어 준 역량 리포트.
 *
 * <p>AI 서버는 snake_case 로 답한다. RestClient 는 Jackson 3 으로 읽으므로 이름 규칙도 Jackson 3 의 어노테이션으로 단다.
 *
 * @param categories 평가 축별 점수. 축을 보내지 않았거나 자료가 없어 분석하지 않았으면 비어 있다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AnalysisAiResponse(
        String title,
        int totalScore,
        String summary,
        String githubAnalysis,
        String portfolioAnalysis,
        List<AnalyzedStack> stacks,
        List<CategoryScore> categories,
        Sources sources
) {

    public AnalysisAiResponse {
        stacks = stacks == null ? List.of() : List.copyOf(stacks);
        categories = categories == null ? List.of() : List.copyOf(categories);
    }

    /**
     * 분석이 찾아낸 기술 하나.
     *
     * @param level    백엔드의 StackLevel 과 같은 값 (BEGINNER / INTERMEDIATE / ADVANCED / EXPERT)
     * @param category 이 기술이 속한 평가 축의 code. 축을 보내지 않았으면 비어 있다
     * @param foundIn  근거를 찾은 곳 (GITHUB / PORTFOLIO)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record AnalyzedStack(String name, int score, String level, String description,
                                String category, List<String> foundIn) {

        public static final String GITHUB = "GITHUB";
        public static final String PORTFOLIO = "PORTFOLIO";

        public AnalyzedStack {
            foundIn = foundIn == null ? List.of() : List.copyOf(foundIn);
        }

        public boolean foundInGithub() {
            return foundIn.contains(GITHUB);
        }

        public boolean foundInPortfolio() {
            return foundIn.contains(PORTFOLIO);
        }
    }

    /** 평가 축 하나에 대한 점수와 해석. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CategoryScore(String code, int score, String interpretation) {
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
