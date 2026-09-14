package com.roddy.domain.graph.dto;

/**
 * 그래프 갱신 결과.
 *
 * @param technologyStackCount 사전과 맞춘 기술 노드 수
 * @param autoRelationCount    새로 만든 자동 USED_WITH 관계 수
 */
public record GraphRebuildResponse(int technologyStackCount, int autoRelationCount) {
}
