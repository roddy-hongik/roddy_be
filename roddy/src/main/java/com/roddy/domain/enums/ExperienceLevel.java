package com.roddy.domain.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExperienceLevel {
    NEWBIE("신입"),
    JUNIOR("1~3년차"),
    MIDDLE("4~7년차"),
    SENIOR("8년차 이상");

    private final String description;
}