package com.roddy.domain.jobposting.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import com.roddy.domain.jobposting.entity.CrawlRun;

/** 어드민 수집 현황에서 회사 한 곳의 상태를 나타내는 신호등. */
public enum CrawlingHealth {

    HEALTHY("healthy"),
    WARNING("warning"),
    ERROR("error");

    private final String value;

    CrawlingHealth(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    /**
     * 마지막 수집 결과로 상태를 정한다.
     *
     * <p>수집 이력이 아예 없는 회사는 주의로 본다. 실패한 것은 아니지만 명세만 있고 한 번도 돌지
     * 않았다는 뜻이라 확인이 필요하다.
     */
    public static CrawlingHealth from(CrawlRun latestRun) {
        if (latestRun == null) {
            return WARNING;
        }
        if (latestRun.getStatus() == CrawlRunStatus.FAILED) {
            return ERROR;
        }
        return latestRun.getStatus() == CrawlRunStatus.SUCCESS ? HEALTHY : WARNING;
    }
}
