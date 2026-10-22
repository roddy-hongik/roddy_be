package com.roddy.domain.jobposting.service;

import com.roddy.domain.enums.DesiredJob;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 제목을 우선 사용하고, 제목에 직무가 없을 때만 수집된 직무 분야를 사용한다. */
public final class JobRoleClassifier {
    private JobRoleClassifier() {}

    private record Rule(DesiredJob job, Pattern pattern) {}

    private static Rule rule(DesiredJob job, String expression) {
        return new Rule(job, Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }

    private static final List<Rule> RULES = List.of(
            rule(DesiredJob.BACKEND, "백엔드|백[ -]?엔드|(?<!게임\\s*)서버\\s*개발|(?<![a-z])back[ -]?end(?![a-z])|(?<![a-z])(?<!game\\s*)server\\s*(developer|engineer)"),
            rule(DesiredJob.FRONTEND, "프론트엔드|프론트[ -]?엔드|(?<![a-z])front[ -]?end(?![a-z])"),
            rule(DesiredJob.FULLSTACK, "풀스택|풀[ -]?스택|(?<![a-z])full[ -]?stack(?![a-z])"),
            rule(DesiredJob.IOS, "(?<![a-z])ios(?![a-z])|아이폰\\s*개발"),
            rule(DesiredJob.ANDROID, "(?<![a-z])android(?![a-z])|안드로이드"),
            rule(DesiredJob.AI_ML, "(?<![a-z])(?:ai|ml)(?![a-z])|머신\\s*러닝|딥\\s*러닝|인공지능|machine\\s*learning|deep\\s*learning"),
            rule(DesiredJob.DATA_ENGINEER, "데이터\\s*(엔지니어|엔지니어링)|data[ -]*(engineer|engineering)"),
            rule(DesiredJob.DEVOPS, "(?<![a-z])(?:devops|sre)(?![a-z])|인프라\\s*(엔지니어|개발|운영)|infrastructure\\s*(engineer|developer)|site\\s*reliability"),
            rule(DesiredJob.SECURITY, "보안\\s*(엔지니어|개발|담당|운영)|정보\\s*보안|security\\s*(engineer|developer)"),
            rule(DesiredJob.GAME, "게임\\s*(개발|서버|클라이언트|엔진)|game\\s*(developer|engineer|server|client)|(?<![a-z])(?:unity|unreal)(?![a-z])")
    );

    public static DesiredJob classify(String title, String recruitField) {
        Set<DesiredJob> titleMatches = matches(title);
        if (!titleMatches.isEmpty()) {
            return single(titleMatches);
        }
        return single(matches(recruitField));
    }

    private static Set<DesiredJob> matches(String text) {
        Set<DesiredJob> found = EnumSet.noneOf(DesiredJob.class);
        if (text != null) {
            RULES.stream().filter(rule -> rule.pattern().matcher(text).find())
                    .forEach(rule -> found.add(rule.job()));
        }
        return found;
    }

    private static DesiredJob single(Set<DesiredJob> found) {
        // 명시적인 풀스택 공고의 프론트/백엔드 병기는 하나의 직무로 본다.
        if (found.contains(DesiredJob.FULLSTACK)) {
            found.remove(DesiredJob.BACKEND);
            found.remove(DesiredJob.FRONTEND);
        }
        return found.size() == 1 ? found.iterator().next() : null;
    }
}
