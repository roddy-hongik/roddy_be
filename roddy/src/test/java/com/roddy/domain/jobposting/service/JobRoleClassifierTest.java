package com.roddy.domain.jobposting.service;

import com.roddy.domain.enums.DesiredJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class JobRoleClassifierTest {
    @ParameterizedTest
    @CsvSource({
            "백엔드 개발자,BACKEND", "Back-end Engineer,BACKEND", "서버 개발자,BACKEND",
            "Frontend Engineer,FRONTEND", "프론트엔드 개발자,FRONTEND",
            "Full Stack Developer,FULLSTACK", "풀스택 백엔드 프론트엔드 개발자,FULLSTACK",
            "iOS Engineer,IOS", "Android 개발자,ANDROID", "ML Engineer,AI_ML",
            "머신러닝 엔지니어,AI_ML", "Data Engineer,DATA_ENGINEER", "데이터 엔지니어,DATA_ENGINEER",
            "SRE Engineer,DEVOPS", "인프라 엔지니어,DEVOPS", "Security Engineer,SECURITY",
            "정보보안 담당자,SECURITY", "게임 클라이언트 개발자,GAME", "Unity Developer,GAME"
    })
    void recognizesExplicitRoles(String title, DesiredJob expected) {
        assertThat(JobRoleClassifier.classify(title, null)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"개발자 채용", "영업 매니저", "AI/ML 및 Backend Engineer", "iOS / Android 개발", "Frontend Backend 개발자", "Retail Manager"})
    void leavesUnknownOrAmbiguousRolesUnclassified(String title) {
        assertThat(JobRoleClassifier.classify(title, "Engineering")).isNull();
    }

    @Test
    void prefersTitleAndFallsBackToField() {
        assertThat(JobRoleClassifier.classify("백엔드 개발자", "AI/ML")).isEqualTo(DesiredJob.BACKEND);
        assertThat(JobRoleClassifier.classify("신입 채용", "Data Engineer")).isEqualTo(DesiredJob.DATA_ENGINEER);
        assertThat(JobRoleClassifier.classify(null, null)).isNull();
        assertThat(JobRoleClassifier.classify("iOS / Android", "Backend")).isNull();
    }
}
