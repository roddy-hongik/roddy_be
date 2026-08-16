package com.roddy.domain.community.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommunityInterviewSubtype {
    ACCEPTED("합격 후기"),
    INCUMBENT("현직자 인터뷰");

    private final String displayName;
}
