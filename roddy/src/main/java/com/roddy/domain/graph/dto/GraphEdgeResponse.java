package com.roddy.domain.graph.dto;

import com.roddy.domain.graph.enums.GraphRelationType;

/**
 * 관계 하나.
 *
 * @param createdBy  auto(모집 중인 공고로 자동 계산) 또는 manual(어드민이 만들거나 고침)
 * @param confidence 0~1. 자동 관계는 함께 요구된 비율이고, 어드민이 만든 관계는 1.0 이다
 */
public record GraphEdgeResponse(
        String id,
        String source,
        GraphRelationType relationType,
        String target,
        String createdBy,
        double confidence,
        String description
) {
}
