package com.roddy.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StudyApplyStatus {
    PENDING("승인 대기중"),
    APPROVED("참여 승인됨"),
    REJECTED("참여 거절됨");

    private final String description;
}