package com.roddy.domain.analysis.entity;

import com.roddy.domain.enums.Stack;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stacks")
public class StackDetail {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stack_id")
    private Long id;

    /**
     * 로드맵에서 쓰는 역량 분류. 분석이 만든 행은 아직 정하지 않는다.
     *
     * <p>분석은 "Java", "Spring Boot" 같은 구체적인 기술을 내놓는데, 이 enum 은 아키텍처·확장성처럼
     * 로드맵이 쓰는 상위 분류라 자동으로 정할 수 없다.
     */
    @Enumerated(EnumType.STRING)
    private Stack stack;

    private String stackName;

    private String description;

    /** 분석이 찾아낸 기술. 로드맵 분류는 나중에 정한다. */
    public static StackDetail ofName(String stackName) {
        return create(null, stackName, null);
    }

    public static StackDetail create(Stack stack, String stackName, String description) {
        StackDetail detail = new StackDetail();
        detail.stack = stack;
        detail.stackName = stackName;
        detail.description = description;
        return detail;
    }
}