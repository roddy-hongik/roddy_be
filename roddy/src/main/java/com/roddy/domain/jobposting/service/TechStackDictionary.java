package com.roddy.domain.jobposting.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 채용공고 본문에서 찾아낼 기술스택 사전.
 *
 * <p>사전은 YAML 로 관리한다. 기술 이름이 늘어날 때 코드를 고치지 않아도 되고, 무엇을 찾고 있는지
 * 한눈에 보이기 때문이다.
 */
@Component
public class TechStackDictionary {

    private static final String DICTIONARY_PATH = "jobposting/tech-stacks.yaml";

    /**
     * 앞뒤가 영문자나 숫자로 이어지면 다른 낱말의 일부다. "JavaScript" 안의 "Java" 를 걸러낸다.
     *
     * <p>한글은 경계로 보지 않는다. "Java를", "Spring을" 처럼 조사가 바로 붙는 표기가 흔해서,
     * 한글을 경계에 넣으면 그런 공고를 통째로 놓친다. 대신 "자바스크립트" 안의 "자바" 같은 겹침은
     * 긴 표기를 먼저 찾아 그 자리를 가리는 방식으로 막는다.
     */
    private static final String BOUNDARY_BEFORE = "(?<![A-Za-z0-9])";
    private static final String BOUNDARY_AFTER = "(?![A-Za-z0-9])";

    private final List<Term> terms;
    private final List<StackEntry> stacks;

    public TechStackDictionary() {
        Dictionary dictionary = read();
        this.terms = loadTerms(dictionary);
        this.stacks = dictionary.stacks().stream()
                .map(stack -> new StackEntry(stack.name(), stack.category()))
                .toList();
    }

    /** 표기가 긴 것부터 정렬된 목록. 겹치는 표기를 가려내는 순서가 된다. */
    public List<Term> terms() {
        return terms;
    }

    /** 사전에 실린 기술과 그 분류. 기술 그래프의 노드가 된다. */
    public List<StackEntry> stacks() {
        return stacks;
    }

    private List<Term> loadTerms(Dictionary dictionary) {
        List<Entry> entries = new ArrayList<>();
        for (Stack stack : dictionary.stacks()) {
            entries.add(new Entry(stack.name(), stack.name()));
            stack.aliases().forEach(alias -> entries.add(new Entry(stack.name(), alias)));
        }

        // 표기가 긴 것부터 찾아야 "C++" 를 "C" 로, "자바스크립트" 를 "자바" 로 잘못 잡지 않는다.
        entries.sort(Comparator.comparingInt((Entry entry) -> entry.text().length()).reversed());

        return entries.stream()
                .map(entry -> new Term(entry.stackName(), compile(entry.text())))
                .toList();
    }

    private Dictionary read() {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream in = new ClassPathResource(DICTIONARY_PATH).getInputStream()) {
            return yamlMapper.readValue(in, Dictionary.class);
        } catch (IOException e) {
            throw new IllegalStateException("기술스택 사전을 읽지 못했습니다: %s".formatted(DICTIONARY_PATH), e);
        }
    }

    private Pattern compile(String text) {
        return Pattern.compile(
                BOUNDARY_BEFORE + Pattern.quote(text) + BOUNDARY_AFTER,
                Pattern.CASE_INSENSITIVE);
    }

    /** 사전에 실린 표기 하나와 그 표기가 가리키는 기술 이름. */
    public record Term(String stackName, Pattern pattern) {
    }

    /** 사전에 실린 기술 하나. category 는 기술 그래프에서 노드를 나누는 분류다. */
    public record StackEntry(String name, String category) {
    }

    private record Entry(String stackName, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Dictionary(List<Stack> stacks) {

        private Dictionary {
            stacks = stacks == null ? List.of() : List.copyOf(stacks);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Stack(String name, String category, List<String> aliases) {

        private Stack {
            category = category == null ? "etc" : category;
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }
    }
}
