package com.roddy.domain;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Stack stack;

    private String stackName;

    private String description;
}