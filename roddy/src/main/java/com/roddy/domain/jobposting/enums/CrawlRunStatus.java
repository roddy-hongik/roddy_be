package com.roddy.domain.jobposting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 회사 한 곳의 수집 회차 결과. */
@Getter
@RequiredArgsConstructor
public enum CrawlRunStatus {

    /** 점검을 통과했고 적재도 전부 성공했다. */
    SUCCESS("성공"),

    /** 수집은 됐지만 점검에 걸렸거나 일부 공고가 실패했다. */
    PARTIAL("일부 실패"),

    /** 수집 자체가 실패해 아무것도 적재하지 못했다. */
    FAILED("실패");

    private final String displayName;
}
