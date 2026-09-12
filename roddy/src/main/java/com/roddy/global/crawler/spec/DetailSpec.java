package com.roddy.global.crawler.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 공고 상세 페이지 수집 설정.
 *
 * <p>아직 {@code enabled} 만 읽는다. 목록 응답에 본문이 없는 회사가 많아 상세 수집이 필요하지만,
 * 실제 수집은 후속 작업에서 붙인다. 지금은 명세에 상세 설정이 있는데 수집되지 않는다는 사실을
 * {@link com.roddy.global.crawler.engine.DeclarativeCrawler} 가 경고로 남기는 용도로만 쓴다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DetailSpec(boolean enabled) {
}
