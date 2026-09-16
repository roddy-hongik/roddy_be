package com.roddy.domain.graph.dto;

/**
 * 기술 노드 하나.
 *
 * @param id            기술 이름. 노드는 이름으로 구분한다
 * @param category      기술 사전의 분류 (language, backend, ...)
 * @param relationCount 지우지 않은 관계 수
 */
public record GraphNodeResponse(String id, String name, String category, long relationCount) {
}
