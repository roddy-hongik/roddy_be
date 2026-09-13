package com.roddy.domain.analysis.service;

import com.roddy.domain.analysis.dto.CompetencyCategory;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.enums.Stack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompetencyCategoryCatalogTest {

    private final CompetencyCategoryCatalog catalog = new CompetencyCategoryCatalog();

    @Test
    @DisplayName("백엔드 축은 로드맵 분류(Stack)와 같은 code 를 같은 순서로 쓴다")
    void backendCategoriesFollowStack() {
        assertThat(catalog.categoriesOf(DesiredJob.BACKEND))
                .extracting(CompetencyCategory::code)
                .containsExactly(Arrays.stream(Stack.values()).map(Enum::name).toArray(String[]::new));
    }

    @Test
    @DisplayName("풀스택은 백엔드와 다른 축을 쓴다")
    void fullstackHasOwnCategories() {
        assertThat(catalog.categoriesOf(DesiredJob.FULLSTACK))
                .extracting(CompetencyCategory::code)
                .containsExactly("BACKEND", "FRONTEND", "DATABASE", "CS_FUNDAMENTALS", "COLLABORATION");
    }

    @Test
    @DisplayName("축을 정하지 않은 직무나 직무가 비어 있으면 빈 목록이다")
    void returnsEmptyForUndefinedJob() {
        assertThat(catalog.categoriesOf(DesiredJob.FRONTEND)).isEmpty();
        assertThat(catalog.categoriesOf(null)).isEmpty();
    }

    @Test
    @DisplayName("한 직무에 같은 code 가 두 번 있으면 읽지 않는다")
    void rejectsDuplicateCode() {
        assertThatThrownBy(() -> new CompetencyCategoryCatalog("analysis/competency-categories-duplicate.yaml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ARCHITECTURE");
    }

    @Test
    @DisplayName("예전 직무 값에는 축을 둘 수 없다")
    void rejectsLegacyJob() {
        assertThatThrownBy(() -> new CompetencyCategoryCatalog("analysis/competency-categories-legacy-job.yaml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BEGINNER");
    }
}
