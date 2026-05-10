package com.roddy.domain.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PostCategory {

    FREE("자유"),
    QUESTION("질문"),
    INFO("정보"),
    REVIEW("후기"),        // 면접 후기, 기업 후기
    RECRUIT("채용공고");   // 채용 정보 공유

    private final String displayName;
}
