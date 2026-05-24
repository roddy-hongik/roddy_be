package com.roddy.domain.community.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommunityPostCategory {
    FREE("자유글"),
    ROADMAP("로드맵 공유"),
    PASS_REVIEW_INTERVIEW("합격 후기 / 인터뷰");

    private final String displayName;
}
