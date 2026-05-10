package com.roddy.domain.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JobPostingStatus {
    OPEN("모집중"),
    CLOSED("마감");
    private final String displayName;
}
