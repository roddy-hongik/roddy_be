package com.roddy.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RecruitType {

    INTERN("인턴"),
    JUNIOR("신입"),
    SENIOR("경력직");

    private final String displayName;
}
