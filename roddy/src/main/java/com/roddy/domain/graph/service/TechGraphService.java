package com.roddy.domain.graph.service;

import com.roddy.domain.enums.JobPostingStatus;
import com.roddy.domain.graph.dto.GraphEdgeRequest;
import com.roddy.domain.graph.dto.GraphEdgeResponse;
import com.roddy.domain.graph.dto.GraphNodeResponse;
import com.roddy.domain.graph.dto.GraphRebuildResponse;
import com.roddy.domain.graph.dto.GraphSearchResponse;
import com.roddy.domain.graph.repository.TechGraphRepository;
import com.roddy.domain.jobposting.repository.JobPostingRepository;
import com.roddy.domain.jobposting.service.TechStackDictionary;
import com.roddy.domain.jobposting.service.TechStackExtractor;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 기술 그래프.
 *
 * <p>노드는 기술 사전의 기술이고, USED_WITH 관계는 모집 중인 공고에서 함께 요구되는 기술로 자동으로 만든다.
 * 어드민은 관계를 더하거나 고치거나 지울 수 있고, 그렇게 손댄 관계는 자동 계산이 덮어쓰지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TechGraphService {

    private final TechGraphRepository techGraphRepository;
    private final TechStackDictionary techStackDictionary;
    private final TechStackExtractor techStackExtractor;
    private final JobPostingRepository jobPostingRepository;

    /** 사전의 기술을 노드로 맞추고, 모집 중인 공고로 자동 관계를 다시 계산한다. */
    public GraphRebuildResponse rebuild() {
        List<TechStackDictionary.StackEntry> stacks = techStackDictionary.stacks();

        Map<Long, Set<String>> stacksByPosting = new HashMap<>();
        for (Object[] row : jobPostingRepository.findTechStacksByStatus(JobPostingStatus.OPEN)) {
            stacksByPosting.computeIfAbsent((Long) row[0], id -> new HashSet<>()).add((String) row[1]);
        }
        List<TechCoOccurrence> pairs = TechCoOccurrence.calculate(stacksByPosting.values());

        int created = graph(() -> {
            techGraphRepository.syncStacks(stacks);
            return techGraphRepository.replaceAutoUsedWith(pairs);
        });

        log.info("기술 그래프를 갱신했습니다. 기술 {}개 / 공고 {}건 / 자동 관계 {}개", stacks.size(), stacksByPosting.size(), created);
        return new GraphRebuildResponse(stacks.size(), created);
    }

    /** 수집이 끝나면 부른다. 그래프가 떠 있지 않거나 갱신이 실패해도 수집 결과에는 영향을 주지 않는다. */
    public void rebuildAfterCrawl() {
        try {
            rebuild();
        } catch (RuntimeException e) {
            log.warn("수집 뒤 기술 그래프를 갱신하지 못했습니다.", e);
        }
    }

    /** 기술 이름이나 별칭으로 노드와 그 관계를 찾는다. */
    public GraphSearchResponse search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return GraphSearchResponse.notFound();
        }

        String name = techStackExtractor.canonicalize(keyword);
        return graph(() -> techGraphRepository.findNode(name)
                .map(node -> new GraphSearchResponse(node, techGraphRepository.findEdges(node.name())))
                .orElseGet(GraphSearchResponse::notFound));
    }

    public GraphEdgeResponse createEdge(GraphEdgeRequest request) {
        return graph(() -> {
            Ends ends = resolveEnds(request);
            if (techGraphRepository.existsManualEdge(ends.source(), request.relationType(), ends.target(), "")) {
                throw new GeneralException(GeneralErrorCode.GRAPH_EDGE_DUPLICATED);
            }
            return techGraphRepository.createManualEdge(UUID.randomUUID().toString(), ends.source(),
                    request.relationType(), ends.target(), description(request));
        });
    }

    public GraphEdgeResponse updateEdge(String edgeId, GraphEdgeRequest request) {
        return graph(() -> {
            if (techGraphRepository.findEdge(edgeId).isEmpty()) {
                throw new GeneralException(GeneralErrorCode.GRAPH_EDGE_NOT_FOUND);
            }
            Ends ends = resolveEnds(request);
            if (techGraphRepository.existsManualEdge(ends.source(), request.relationType(), ends.target(), edgeId)) {
                throw new GeneralException(GeneralErrorCode.GRAPH_EDGE_DUPLICATED);
            }
            return techGraphRepository.replaceEdge(edgeId, ends.source(), request.relationType(), ends.target(),
                    description(request));
        });
    }

    public void deleteEdge(String edgeId) {
        boolean deleted = graph(() -> techGraphRepository.deleteEdge(edgeId));
        if (!deleted) {
            throw new GeneralException(GeneralErrorCode.GRAPH_EDGE_NOT_FOUND);
        }
    }

    /** 별칭을 표준 이름으로 바꾸고, 그래프에 있는 기술인지 확인한다. */
    private Ends resolveEnds(GraphEdgeRequest request) {
        String source = requireNode(request.source());
        String target = requireNode(request.target());
        if (source.equals(target)) {
            throw new GeneralException(GeneralErrorCode.GRAPH_EDGE_SELF_LOOP);
        }
        return new Ends(source, target);
    }

    private String requireNode(String name) {
        return techGraphRepository.findNode(techStackExtractor.canonicalize(name))
                .map(GraphNodeResponse::name)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.GRAPH_TECH_STACK_NOT_FOUND));
    }

    private String description(GraphEdgeRequest request) {
        return request.description() == null || request.description().isBlank() ? null : request.description().trim();
    }

    /** 그래프 저장소에 닿지 못하면 잠시 쓸 수 없다고 답한다. */
    private <T> T graph(Supplier<T> work) {
        try {
            return work.get();
        } catch (Neo4jException e) {
            log.error("기술 그래프 저장소 요청이 실패했습니다.", e);
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private record Ends(String source, String target) {
    }
}
