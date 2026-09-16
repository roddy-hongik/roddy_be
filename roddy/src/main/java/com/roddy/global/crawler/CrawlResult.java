package com.roddy.global.crawler;

import java.util.List;

/**
 * 회사 한 곳의 수집 결과.
 *
 * @param issues 점검에서 걸린 문제. 비어 있으면 명세가 사이트와 어긋나지 않았다는 뜻이다.
 */
public record CrawlResult(String company, List<CrawlRecord> records, List<String> issues) {

    public CrawlResult {
        records = List.copyOf(records);
        issues = List.copyOf(issues);
    }

    public boolean isHealthy() {
        return issues.isEmpty();
    }

    public int size() {
        return records.size();
    }

    /** 상세 페이지를 받아오지 못한 공고 수. 본문이 비어 있는 이유를 추적할 때 쓴다. */
    public long detailFailureCount() {
        return records.stream().filter(CrawlRecord::hasDetailError).count();
    }
}
