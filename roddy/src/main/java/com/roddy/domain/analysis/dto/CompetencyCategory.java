package com.roddy.domain.analysis.dto;

/**
 * 역량 분석 리포트의 평가 축 하나.
 *
 * @param code        리포트에 저장하는 값. 한번 쓴 값은 바꾸지 않는다
 * @param name        화면에 보여줄 이름
 * @param description 이 축에서 무엇을 보는지. AI 서버에도 채점 기준으로 넘긴다
 */
public record CompetencyCategory(String code, String name, String description) {
}
