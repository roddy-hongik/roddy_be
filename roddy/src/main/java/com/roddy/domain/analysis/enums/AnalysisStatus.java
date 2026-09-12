package com.roddy.domain.analysis.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 역량 분석의 진행 상태. 분석은 깃허브를 훑고 LLM 을 부르느라 오래 걸려 즉시 끝나지 않는다. */
@Getter
@RequiredArgsConstructor
public enum AnalysisStatus {

    PENDING("분석 중"),
    COMPLETED("완료"),
    FAILED("실패");

    private final String displayName;
}
