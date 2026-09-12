package com.roddy.domain.jobposting.service;

import com.roddy.domain.jobposting.dto.JobPostingSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * 공고 글에서 요구 기술스택을 뽑아낸다.
 *
 * <p>사전에 실린 표기를 찾는 방식이라 비용이 들지 않고 언제 돌려도 같은 결과가 나온다. 사전에 없는
 * 기술은 잡지 못하므로, 놓치는 게 많아지면 사전을 늘리거나 LLM 추출을 덧붙이면 된다.
 */
@Component
@RequiredArgsConstructor
public class TechStackExtractor {

    private static final char MASK = ' ';

    private final TechStackDictionary dictionary;

    /** 제목 / 직무 분류 / 본문을 함께 본다. 본문이 없는 공고도 제목에서 건질 게 있다. */
    public Set<String> extract(JobPostingSnapshot snapshot) {
        return extract(snapshot.title(), snapshot.recruitField(), snapshot.content());
    }

    public Set<String> extract(String... texts) {
        StringBuilder haystack = new StringBuilder();
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                haystack.append(text).append('\n');
            }
        }
        if (haystack.isEmpty()) {
            return Set.of();
        }

        Set<String> found = new LinkedHashSet<>();
        for (TechStackDictionary.Term term : dictionary.terms()) {
            List<int[]> ranges = findAll(term, haystack);
            if (ranges.isEmpty()) {
                continue;
            }

            found.add(term.stackName());
            // 찾은 자리를 가려 두면 더 짧은 표기가 같은 자리에서 다시 잡히지 않는다.
            ranges.forEach(range -> mask(haystack, range[0], range[1]));
        }
        return found;
    }

    /** 찾는 도중에 글자를 바꾸면 안 되므로 위치부터 모아 둔다. */
    /**
     * 사용자가 적어 둔 기술 이름을 사전의 표준 이름으로 맞춘다.
     *
     * <p>"자바" 와 "java" 가 공고의 "Java" 와 이어지도록 하기 위함이다. 사전에 없거나 여러 기술이
     * 섞여 읽히면 다듬기만 해서 그대로 돌려준다.
     */
    public String canonicalize(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        Set<String> matched = extract(name);
        return matched.size() == 1 ? matched.iterator().next() : name.trim();
    }

    private List<int[]> findAll(TechStackDictionary.Term term, CharSequence haystack) {
        Matcher matcher = term.pattern().matcher(haystack);
        List<int[]> ranges = new ArrayList<>();
        while (matcher.find()) {
            ranges.add(new int[]{matcher.start(), matcher.end()});
        }
        return ranges;
    }

    /** 길이는 그대로 두고 공백으로만 덮으므로 앞서 찾아 둔 위치가 어긋나지 않는다. */
    private void mask(StringBuilder haystack, int start, int end) {
        for (int i = start; i < end; i++) {
            haystack.setCharAt(i, MASK);
        }
    }
}
