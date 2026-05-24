package com.roddy.domain.study.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StudyRecruitStatus {
    RECRUITING("모집중"),
    CLOSED("모집완료");

    private final String displayName;
}
