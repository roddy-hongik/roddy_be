package com.roddy.global.crawler.engine;

import com.roddy.global.crawler.CrawlRecord;
import com.roddy.global.crawler.spec.CrawlSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 수집 결과를 비용 없이 점검한다.
 *
 * <p>사이트가 개편되면 명세가 조용히 어긋나 0건이 수집되거나 필수 필드가 통째로 비는 식으로 깨진다.
 * 이 점검이 그걸 잡아내는 1차 방어선이다.
 */
public final class SpecChecker {

    private SpecChecker() {
    }

    /** 문제가 없으면 빈 목록을 돌려준다. */
    public static List<String> check(List<CrawlRecord> records, CrawlSpec spec) {
        if (records.isEmpty()) {
            return List.of("0건 수집 — url / 응답 경로 / 필터를 확인해야 합니다.");
        }

        List<String> issues = new ArrayList<>();
        for (String field : spec.required()) {
            long missing = records.stream().filter(record -> record.isBlank(field)).count();
            if (missing > 0) {
                issues.add("필수 필드 '%s' 가 비어 있습니다: %d/%d".formatted(field, missing, records.size()));
            }
        }

        long unique = records.stream().map(record -> record.text(CrawlRecord.JOB_ID)).distinct().count();
        if (unique != records.size()) {
            issues.add("job_id 중복: 고유 %d / 전체 %d".formatted(unique, records.size()));
        }
        return issues;
    }
}
