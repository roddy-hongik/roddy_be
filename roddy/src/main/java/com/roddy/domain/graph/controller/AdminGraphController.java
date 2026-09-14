package com.roddy.domain.graph.controller;

import com.roddy.domain.graph.dto.GraphEdgeRequest;
import com.roddy.domain.graph.dto.GraphEdgeResponse;
import com.roddy.domain.graph.dto.GraphRebuildResponse;
import com.roddy.domain.graph.dto.GraphSearchResponse;
import com.roddy.domain.graph.service.TechGraphService;
import com.roddy.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/graph")
@RequiredArgsConstructor
@Tag(name = "AdminGraph", description = "어드민 기술 그래프 관리 API")
public class AdminGraphController {

    private final TechGraphService techGraphService;

    @GetMapping("/search")
    @Operation(summary = "기술 노드 검색", description = "기술 이름이나 별칭으로 노드와 그 관계를 찾는다. 없는 기술이면 searchedNode 가 null")
    public ApiResponse<GraphSearchResponse> search(@RequestParam(defaultValue = "") String keyword) {
        return ApiResponse.onSuccess("기술 그래프를 검색했습니다.", techGraphService.search(keyword));
    }

    @PostMapping("/edges")
    @Operation(summary = "관계 추가", description = "어드민이 만든 관계는 자동 계산이 덮어쓰지 않는다. 같은 쌍의 자동 관계는 이 관계가 대신한다")
    public ApiResponse<GraphEdgeResponse> createEdge(@Valid @RequestBody GraphEdgeRequest request) {
        return ApiResponse.onSuccess("관계를 추가했습니다.", techGraphService.createEdge(request));
    }

    @PutMapping("/edges/{edgeId}")
    @Operation(summary = "관계 수정", description = "고친 관계는 어드민이 만든 관계가 된다")
    public ApiResponse<GraphEdgeResponse> updateEdge(@PathVariable String edgeId,
                                                     @Valid @RequestBody GraphEdgeRequest request) {
        return ApiResponse.onSuccess("관계를 수정했습니다.", techGraphService.updateEdge(edgeId, request));
    }

    @DeleteMapping("/edges/{edgeId}")
    @Operation(summary = "관계 삭제", description = "지운 자동 관계는 다음 자동 계산 때 다시 생기지 않는다")
    public ApiResponse<Void> deleteEdge(@PathVariable String edgeId) {
        techGraphService.deleteEdge(edgeId);
        return ApiResponse.onSuccess("관계를 삭제했습니다.");
    }

    @PostMapping("/rebuild")
    @Operation(summary = "그래프 갱신", description = "사전의 기술을 노드로 맞추고 모집 중인 공고로 자동 관계를 다시 계산한다. 수집이 끝날 때도 자동으로 돈다")
    public ApiResponse<GraphRebuildResponse> rebuild() {
        return ApiResponse.onSuccess("기술 그래프를 갱신했습니다.", techGraphService.rebuild());
    }
}
