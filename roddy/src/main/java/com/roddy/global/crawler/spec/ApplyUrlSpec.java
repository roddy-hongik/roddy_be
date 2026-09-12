package com.roddy.global.crawler.spec;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 응답에 지원 URL 이 없고 ID 만 있는 회사를 위한 조립 규칙.
 *
 * <p>{@code https://careers.kakao.com/jobs/{job_id}} 처럼 중괄호 자리에 레코드의 필드 값을 끼운다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApplyUrlSpec(String template) {
}
