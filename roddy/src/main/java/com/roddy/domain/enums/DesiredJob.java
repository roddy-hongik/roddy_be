package com.roddy.domain.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DesiredJob {

    BACKEND("백엔드 개발자"),
    FRONTEND("프론트엔드 개발자"),
    FULLSTACK("풀스택 개발자"),
    IOS("iOS 개발자"),
    ANDROID("안드로이드 개발자"),
    AI_ML("AI/ML 엔지니어"),
    DATA_ENGINEER("데이터 엔지니어"),
    DEVOPS("DevOps 엔지니어"),
    SECURITY("보안 엔지니어"),
    GAME("게임 개발자");

    private final String description;

}
