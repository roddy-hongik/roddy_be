package com.roddy.domain.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Stack {

    DATA_MODELING("Data Modeling"),
    ARCHITECTURE("Architecture"),
    SCALABILITY("Scalability"),
    STABILITY("Stability"),
    DEVOPS_CICD("DevOps/CICD"),
    MONITORING("Monitoring");

    private final String displayName;

}
