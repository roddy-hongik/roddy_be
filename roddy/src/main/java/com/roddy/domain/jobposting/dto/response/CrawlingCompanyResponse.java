package com.roddy.domain.jobposting.dto.response;

import com.roddy.domain.jobposting.enums.CrawlingHealth;

import java.time.LocalDateTime;

/**
 * 어드민 수집 현황의 회사 한 줄.
 *
 * @param id            수집 명세의 회사 코드 (예: kakao)
 * @param name          표시용 회사명
 * @param collectedToday 오늘 수집된 공고 수
 * @param successCount  오늘 적재에 성공한 공고 수 (신규 + 갱신 + 유지)
 * @param failCount     오늘 적재하지 못한 공고 수
 * @param lastCrawledAt 마지막으로 수집을 시도한 시각. 한 번도 돈 적이 없으면 null
 */
public record CrawlingCompanyResponse(
        String id,
        String name,
        int collectedToday,
        int successCount,
        int failCount,
        LocalDateTime lastCrawledAt,
        CrawlingHealth status
) {
}
