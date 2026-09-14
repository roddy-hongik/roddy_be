package com.roddy.domain.graph.repository;

import com.roddy.domain.graph.dto.GraphEdgeResponse;
import com.roddy.domain.graph.dto.GraphNodeResponse;
import com.roddy.domain.graph.enums.GraphRelationType;
import com.roddy.domain.graph.service.TechCoOccurrence;
import com.roddy.domain.jobposting.service.TechStackDictionary.StackEntry;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.QueryConfig;
import org.neo4j.driver.Record;
import org.neo4j.driver.RoutingControl;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionCallback;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 기술 그래프(Neo4j) 읽기·쓰기.
 *
 * <p>Spring Data 리포지토리 대신 드라이버로 Cypher 를 직접 실행한다. JPA 트랜잭션 매니저와 섞이지 않고,
 * 관계 타입처럼 쿼리 모양이 바뀌는 부분을 드러내 놓고 다루기 위함이다. 관계 타입은 {@link GraphRelationType}
 * 에 있는 이름만 쿼리에 넣는다.
 *
 * <p>관계 속성: {@code id}, {@code createdBy}(auto / manual), {@code confidence}, {@code description},
 * 자동 관계의 {@code coOccurrence}, 어드민이 지운 자동 관계의 {@code deleted}.
 */
@Repository
@RequiredArgsConstructor
public class TechGraphRepository {

    public static final String AUTO = "auto";
    public static final String MANUAL = "manual";

    private static final String EDGE_COLUMNS = """
            r.id AS id, type(r) AS relationType, startNode(r).name AS source, endNode(r).name AS target,
            r.createdBy AS createdBy, r.confidence AS confidence, r.description AS description""";

    private final Driver driver;

    /** 사전의 기술을 노드로 맞춘다. 사전에서 빠진 기술은 관계와 함께 지운다. */
    public void syncStacks(List<StackEntry> stacks) {
        driver.executableQuery(
                "CREATE CONSTRAINT technology_stack_name IF NOT EXISTS FOR (t:TechnologyStack) REQUIRE t.name IS UNIQUE"
        ).execute();

        List<Map<String, Object>> rows = stacks.stream()
                .map(stack -> Map.<String, Object>of("name", stack.name(), "category", stack.category()))
                .toList();
        List<String> names = stacks.stream().map(StackEntry::name).toList();

        write(tx -> {
            tx.run("""
                    UNWIND $stacks AS stack
                    MERGE (t:TechnologyStack {name: stack.name})
                    SET t.category = stack.category
                    """, Map.of("stacks", rows));
            tx.run("MATCH (t:TechnologyStack) WHERE NOT t.name IN $names DETACH DELETE t", Map.of("names", names));
            return null;
        });
    }

    /**
     * 자동 USED_WITH 관계를 새로 계산한 값으로 바꾼다.
     *
     * <p>어드민이 만들거나 고친 관계와 어드민이 지운 자동 관계는 manual 이라 건드리지 않고, 그런 관계가 있는 쌍에는
     * 자동 관계를 다시 만들지 않는다.
     *
     * @return 만든 자동 관계 수
     */
    public int replaceAutoUsedWith(List<TechCoOccurrence> pairs) {
        List<Map<String, Object>> rows = pairs.stream()
                .map(pair -> Map.<String, Object>of(
                        "source", pair.source(), "target", pair.target(),
                        "count", pair.count(), "confidence", pair.confidence()))
                .toList();

        return write(tx -> {
            tx.run("MATCH ()-[r:USED_WITH {createdBy: 'auto'}]->() DELETE r");
            return tx.run("""
                    UNWIND $pairs AS pair
                    MATCH (a:TechnologyStack {name: pair.source}), (b:TechnologyStack {name: pair.target})
                    WHERE NOT EXISTS { (a)-[:USED_WITH {createdBy: 'manual'}]-(b) }
                    CREATE (a)-[:USED_WITH {id: randomUUID(), createdBy: 'auto',
                                            confidence: pair.confidence, coOccurrence: pair.count}]->(b)
                    RETURN count(*) AS created
                    """, Map.of("pairs", rows)).single().get("created").asInt();
        });
    }

    /** 이름으로 기술 노드를 찾는다. 대소문자는 가리지 않는다. */
    public Optional<GraphNodeResponse> findNode(String name) {
        return read("""
                MATCH (t:TechnologyStack) WHERE toLower(t.name) = toLower($name)
                RETURN t.name AS name, t.category AS category,
                       COUNT { (t)-[r]-(:TechnologyStack) WHERE coalesce(r.deleted, false) = false } AS relationCount
                """, Map.of("name", name)).stream()
                .findFirst()
                .map(record -> new GraphNodeResponse(
                        record.get("name").asString(),
                        record.get("name").asString(),
                        record.get("category").asString(null),
                        record.get("relationCount").asLong()));
    }

    /** 기술에 이어진 관계. 신뢰도 높은 순이다. */
    public List<GraphEdgeResponse> findEdges(String name) {
        return read("""
                MATCH (t:TechnologyStack {name: $name})-[r]-(other:TechnologyStack)
                WHERE coalesce(r.deleted, false) = false
                RETURN %s
                ORDER BY confidence DESC, other.name
                """.formatted(EDGE_COLUMNS), Map.of("name", name)).stream()
                .map(this::toEdge)
                .toList();
    }

    public Optional<GraphEdgeResponse> findEdge(String id) {
        return read("""
                MATCH (:TechnologyStack)-[r]->(:TechnologyStack)
                WHERE r.id = $id AND coalesce(r.deleted, false) = false
                RETURN %s
                """.formatted(EDGE_COLUMNS), Map.of("id", id)).stream()
                .findFirst()
                .map(this::toEdge);
    }

    /**
     * 두 기술 사이에 어드민이 만든 같은 타입의 관계가 있는지. 방향이 있는 관계만 방향을 따진다.
     *
     * @param exceptId 수정 중인 관계라 비교에서 뺄 id. 없으면 빈 문자열
     */
    public boolean existsManualEdge(String source, GraphRelationType type, String target, String exceptId) {
        String pattern = type.isDirected() ? "(a)-[r:%s]->(b)" : "(a)-[r:%s]-(b)";
        return read("""
                MATCH (a:TechnologyStack {name: $source}), (b:TechnologyStack {name: $target})
                MATCH %s
                WHERE r.createdBy = 'manual' AND coalesce(r.deleted, false) = false AND r.id <> $exceptId
                RETURN count(r) > 0 AS exists
                """.formatted(pattern.formatted(type.name())),
                Map.of("source", source, "target", target, "exceptId", exceptId)).getFirst().get("exists").asBoolean();
    }

    /** 어드민이 만든 관계. 같은 두 기술 사이의 같은 타입 자동 관계와, 지웠다고 표시해 둔 관계는 이 관계가 대신한다. */
    public GraphEdgeResponse createManualEdge(String id, String source, GraphRelationType type, String target,
                                              String description) {
        return write(tx -> createManualEdge(tx, id, source, type, target, description));
    }

    /** 관계를 새 내용으로 바꾼다. 바꾼 관계는 어드민이 만든 관계가 된다. */
    public GraphEdgeResponse replaceEdge(String id, String source, GraphRelationType type, String target,
                                         String description) {
        return write(tx -> {
            retire(tx, id);
            return createManualEdge(tx, id, source, type, target, description);
        });
    }

    /** @return 지웠으면 true, 그런 관계가 없으면 false */
    public boolean deleteEdge(String id) {
        return write(tx -> retire(tx, id));
    }

    /**
     * 관계를 치운다. 자동으로 다시 생길 수 있는 USED_WITH 는 지웠다고 표시만 해서 다음 자동 계산 때 되살아나지 않게 한다.
     * 표시만 남긴 관계에는 새 id 를 줘서, 같은 id 로 새 관계를 만들 수 있게 한다.
     */
    private boolean retire(org.neo4j.driver.TransactionContext tx, String id) {
        List<Record> found = tx.run("""
                MATCH (:TechnologyStack)-[r]->(:TechnologyStack)
                WHERE r.id = $id AND coalesce(r.deleted, false) = false
                RETURN type(r) AS type
                """, Map.of("id", id)).list();
        if (found.isEmpty()) {
            return false;
        }

        if (GraphRelationType.USED_WITH.name().equals(found.getFirst().get("type").asString())) {
            tx.run("""
                    MATCH ()-[r:USED_WITH]->() WHERE r.id = $id
                    SET r.deleted = true, r.createdBy = 'manual', r.id = randomUUID()
                    """, Map.of("id", id));
        } else {
            tx.run("MATCH ()-[r]->() WHERE r.id = $id DELETE r", Map.of("id", id));
        }
        return true;
    }

    private GraphEdgeResponse createManualEdge(org.neo4j.driver.TransactionContext tx, String id, String source,
                                               GraphRelationType type, String target, String description) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("source", source);
        params.put("target", target);
        params.put("description", description);

        tx.run("""
                MATCH (:TechnologyStack {name: $source})-[r:%s]-(:TechnologyStack {name: $target})
                WHERE r.createdBy = 'auto' OR r.deleted = true
                DELETE r
                """.formatted(type.name()), params);

        return toEdge(tx.run("""
                MATCH (a:TechnologyStack {name: $source}), (b:TechnologyStack {name: $target})
                CREATE (a)-[r:%s {id: $id, createdBy: 'manual', confidence: 1.0, description: $description}]->(b)
                RETURN %s
                """.formatted(type.name(), EDGE_COLUMNS), params).single());
    }

    private GraphEdgeResponse toEdge(Record record) {
        return new GraphEdgeResponse(
                record.get("id").asString(),
                record.get("source").asString(),
                GraphRelationType.valueOf(record.get("relationType").asString()),
                record.get("target").asString(),
                record.get("createdBy").asString(),
                record.get("confidence").asDouble(),
                record.get("description").asString(null));
    }

    private List<Record> read(String cypher, Map<String, Object> params) {
        return driver.executableQuery(cypher)
                .withParameters(params)
                .withConfig(QueryConfig.builder().withRouting(RoutingControl.READ).build())
                .execute()
                .records();
    }

    private <T> T write(TransactionCallback<T> work) {
        try (Session session = driver.session()) {
            return session.executeWrite(work);
        }
    }
}
