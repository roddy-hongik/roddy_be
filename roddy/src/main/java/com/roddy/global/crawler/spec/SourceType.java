package com.roddy.global.crawler.spec;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/** 수집 명세가 가리키는 응답 형태. */
public enum SourceType {

    /** REST JSON API 를 그대로 파싱. */
    JSON,

    /** SSG 페이지에 박힌 {@code <script id="__NEXT_DATA__">} 안의 JSON 을 파싱. */
    EMBEDDED_JSON,

    /** CSS 셀렉터로 HTML 을 파싱. 현재 수집 명세 중 쓰는 회사가 없어 미구현. */
    HTML;

    @JsonCreator
    public static SourceType from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("source_type 이 없습니다.");
        }
        return SourceType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
