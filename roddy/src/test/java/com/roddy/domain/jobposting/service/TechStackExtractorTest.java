package com.roddy.domain.jobposting.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TechStackExtractorTest {

    private final TechStackExtractor extractor = new TechStackExtractor(new TechStackDictionary());

    @Test
    @DisplayName("공고 본문에서 요구 기술을 뽑아낸다")
    void extractsFromContent() {
        String content = """
                [주요 업무]
                - Spring Boot 기반 API 서버 개발
                - MySQL, Redis 를 활용한 데이터 저장소 설계
                [자격 요건]
                - Java 개발 경력 3년 이상
                """;

        assertThat(extractor.extract(content))
                .contains("Spring Boot", "MySQL", "Redis", "Java");
    }

    @Test
    @DisplayName("조사가 바로 붙어도 찾는다")
    void findsTermsFollowedByKoreanParticle() {
        assertThat(extractor.extract("Java를 사용하고 Kubernetes로 배포하며 React가 익숙한 분"))
                .contains("Java", "Kubernetes", "React");
    }

    @Test
    @DisplayName("한글 표기도 찾는다")
    void findsKoreanAliases() {
        assertThat(extractor.extract("자바와 코틀린으로 개발하고 쿠버네티스로 운영합니다."))
                .contains("Java", "Kotlin", "Kubernetes");
    }

    @Test
    @DisplayName("대소문자를 가리지 않는다")
    void ignoresCase() {
        assertThat(extractor.extract("java, TYPESCRIPT, mysql")).contains("Java", "TypeScript", "MySQL");
    }

    @Test
    @DisplayName("다른 낱말에 섞인 표기는 잡지 않는다")
    void doesNotMatchInsideAnotherWord() {
        assertThat(extractor.extract("JavaScript 개발자")).contains("JavaScript").doesNotContain("Java");
        assertThat(extractor.extract("자바스크립트 개발자")).contains("JavaScript").doesNotContain("Java");
    }

    @Test
    @DisplayName("짧은 표기가 긴 표기 자리에서 다시 잡히지 않는다")
    void prefersLongerTerm() {
        assertThat(extractor.extract("C++ 개발 경험")).contains("C++").doesNotContain("C");
        assertThat(extractor.extract("React Native 앱 개발")).contains("React Native").doesNotContain("React");
        assertThat(extractor.extract("Spring Boot 기반")).contains("Spring Boot").doesNotContain("Spring");
    }

    @Test
    @DisplayName("긴 표기와 짧은 표기가 따로 나오면 둘 다 잡는다")
    void findsBothWhenTermsAppearSeparately() {
        assertThat(extractor.extract("C/C++ 로 작성된 엔진")).contains("C", "C++");
    }

    @Test
    @DisplayName("같은 기술이 여러 번 나와도 한 번만 담는다")
    void removesDuplicates() {
        assertThat(extractor.extract("Java 경력, Java 8 이상, 자바 개발"))
                .filteredOn(stack -> stack.equals("Java"))
                .hasSize(1);
    }

    @Test
    @DisplayName("제목과 직무 분류에서도 찾는다")
    void findsInTitleAndRecruitField() {
        assertThat(extractor.extract("백엔드 개발자 (Kotlin)", "Server", null))
                .contains("Kotlin");
    }

    @Test
    @DisplayName("읽을 글이 없으면 빈 결과를 돌려준다")
    void returnsEmptyWithoutText() {
        assertThat(extractor.extract((String) null)).isEmpty();
        assertThat(extractor.extract("", "   ", null)).isEmpty();
    }

    @Test
    @DisplayName("사전에 없는 기술은 잡지 못한다")
    void missesTermsOutsideDictionary() {
        // 사전 매칭 방식의 한계를 드러내 두는 테스트. 놓치는 게 많아지면 사전을 늘려야 한다.
        assertThat(extractor.extract("Zig 와 Gleam 으로 개발합니다.")).isEmpty();
    }
}
