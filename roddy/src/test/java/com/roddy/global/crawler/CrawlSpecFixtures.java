package com.roddy.global.crawler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.roddy.global.crawler.spec.CrawlSpec;

/**
 * 테스트에서 수집 명세를 만드는 헬퍼.
 *
 * <p>명세를 빌더로 조립하는 대신 실제와 같은 YAML 로 쓴다. 그래야 테스트가 곧 명세 예시가 되고
 * YAML 바인딩까지 함께 검증된다.
 */
public final class CrawlSpecFixtures {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final ObjectMapper JSON = new ObjectMapper();

    private CrawlSpecFixtures() {
    }

    public static CrawlSpec spec(String yaml) {
        try {
            return YAML.readValue(yaml, CrawlSpec.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("테스트 명세를 읽지 못했습니다.", e);
        }
    }

    public static JsonNode json(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("테스트 JSON 을 읽지 못했습니다.", e);
        }
    }
}
