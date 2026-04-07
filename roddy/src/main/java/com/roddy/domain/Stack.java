package com.roddy.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stacks")
public class Stack {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stack_id")
    private Long id;

    private String name; // 예: Java, Spring Boot, MySQL

    // 1. Data Modeling, 2. Architecture 등
    private String category;
}