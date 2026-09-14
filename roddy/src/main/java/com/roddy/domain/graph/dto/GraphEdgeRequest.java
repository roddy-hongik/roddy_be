package com.roddy.domain.graph.dto;

import com.roddy.domain.graph.enums.GraphRelationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 어드민이 만들거나 고치는 관계. 기술 이름에는 별칭("자바")을 써도 된다. */
public record GraphEdgeRequest(
        @NotBlank @Size(max = 100) String source,
        @NotNull GraphRelationType relationType,
        @NotBlank @Size(max = 100) String target,
        @Size(max = 500) String description
) {
}
