package com.roddy.domain.study.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StudyMode {
    OFFLINE("대면"),
    ONLINE("비대면");

    private final String displayName;
}
