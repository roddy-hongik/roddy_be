package com.roddy.domain.graph.enums;

/** 기술 사이의 관계. 이름을 Neo4j 관계 타입으로 그대로 쓴다. */
public enum GraphRelationType {
    /** 넓게 관련 있는 기술 */
    RELATED_TO,
    /** 같은 공고에서 함께 요구되는 기술. 모집 중인 공고로 자동으로 만든다 */
    USED_WITH,
    /** 먼저 알아야 하는 기술. source 를 target 보다 먼저 배운다 */
    PREREQUISITE_OF,
    /** 서로 대신 쓸 수 있는 기술 */
    SIMILAR_TO;

    /** 방향이 의미 있는 관계인지. 나머지는 두 기술 사이에 어느 방향으로든 하나만 둔다. */
    public boolean isDirected() {
        return this == PREREQUISITE_OF;
    }
}
