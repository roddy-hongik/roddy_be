package com.roddy.global.crawler.spec;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** {@code resources/crawler/specs} 에 있는 회사별 수집 명세를 읽는다. */
@Component
public class CrawlSpecLoader {

    private static final String SPEC_PATTERN = "classpath:crawler/specs/*.yaml";
    private static final String SPEC_PATH = "classpath:crawler/specs/%s.yaml";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    /** 회사 코드로 명세 하나를 읽는다. 파일명이 곧 회사 코드다 (예: kakao.yaml). */
    public CrawlSpec load(String company) {
        return load(resourceResolver.getResource(SPEC_PATH.formatted(company)));
    }

    /** 회사 코드 순으로 전체 명세를 읽는다. */
    public List<CrawlSpec> loadAll() {
        try {
            Resource[] resources = resourceResolver.getResources(SPEC_PATTERN);
            return Arrays.stream(resources)
                    .map(this::load)
                    .sorted(Comparator.comparing(CrawlSpec::company))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("수집 명세 디렉터리를 읽지 못했습니다.", e);
        }
    }

    public CrawlSpec load(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return yamlMapper.readValue(in, CrawlSpec.class);
        } catch (IOException e) {
            throw new IllegalStateException("수집 명세를 읽지 못했습니다: %s".formatted(resource.getFilename()), e);
        }
    }
}
