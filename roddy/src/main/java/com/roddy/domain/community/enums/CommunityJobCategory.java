package com.roddy.domain.community.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CommunityJobCategory {
    B2C("B2C"),
    FINTECH("금융 및 핀테크"),
    B2B("B2B"),
    INFRA_DEVOPS("Infra/DevOps"),
    GENERALIST("Generalist");

    private final String displayName;
}
