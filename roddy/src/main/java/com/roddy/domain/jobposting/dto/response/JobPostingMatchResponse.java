package com.roddy.domain.jobposting.dto.response;

import java.util.List;

/**
 * 공고 하나와 사용자 기술스택의 적합도.
 *
 * @param matchRate      0~100. 공고가 요구하는 기술마다 사용자의 숙련도 점수를 더해 평균 낸 값이다.
 *                       공고에서 기술을 찾지 못했거나 사용자 분석 결과가 없으면 null 이다.
 *                       그런 경우에 0 을 주면 "적합하지 않다"로 읽히기 때문이다.
 * @param userStackCount 사용자가 가진 기술 수. 0 이면 아직 분석 결과가 없다는 뜻이다.
 * @param missingStacks  공고는 요구하지만 사용자가 갖고 있지 않은 기술. 로드맵 추천의 입력이 된다.
 */
public record JobPostingMatchResponse(
        Long jobPostingId,
        Integer matchRate,
        int requiredCount,
        int matchedCount,
        int userStackCount,
        List<JobMatchStackResponse> stacks,
        List<String> missingStacks
) {

    /** 공고가 요구하는 기술 하나와 그에 대한 사용자의 숙련도. */
    public record JobMatchStackResponse(String name, int userScore, boolean held) {
    }
}
