package com.roddy.domain.jobposting.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 어드민 수집 현황.
 *
 * <p>회사별 채용 사이트를 직접 수집하므로 한 줄이 곧 회사 한 곳이다. 사람인/원티드 같은 채용
 * 플랫폼을 거치지 않는다.
 *
 * @param errorCount   마지막 수집이 실패한 회사 수. 목록이 길어 먼저 볼 곳을 찾기 위한 값
 * @param warningCount 점검에 걸렸거나 아직 한 번도 돌지 않은 회사 수
 */
public record CrawlingDashboardResponse(
        int totalCollectedToday,
        int successCount,
        int failCount,
        LocalDateTime lastCrawledAt,
        int errorCount,
        int warningCount,
        List<CrawlingCompanyResponse> companies
) {
}
