package com.roddy.domain.study.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StudyApplicationStatus {
    APPLIED("지원 완료"),
    ACCEPTED("수락"),
    REJECTED("거절"),
    CANCELED("취소");

    private final String displayName;
}
