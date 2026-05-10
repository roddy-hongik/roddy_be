package com.roddy.domain.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DesiredJob {

    BEGINNER("초급 (기초 문법 및 개념 이해)"),
    INTERMEDIATE("중급 (실무 적용 및 문제 해결 가능)"),
    ADVANCED("고급 (아키텍처 설계 및 성능 최적화 가능)"),
    EXPERT("전문가 (기술 리딩 및 코어 트러블슈팅 가능)");

    private final String description;

}
