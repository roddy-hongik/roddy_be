package com.roddy.domain.graph.dto;

import java.util.List;

/** 기술 검색 결과. 없는 기술이면 searchedNode 가 null 이고 관계도 비어 있다. */
public record GraphSearchResponse(GraphNodeResponse searchedNode, List<GraphEdgeResponse> edges) {

    public static GraphSearchResponse notFound() {
        return new GraphSearchResponse(null, List.of());
    }
}
