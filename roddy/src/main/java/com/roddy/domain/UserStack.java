package com.roddy.domain;

import com.roddy.domain.enums.StackLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_stacks")
public class UserStack extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_stack_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stack_id")
    private Stack stack;

    // 숙련도 (1~5 점수 또는 HIGH/MIDDLE/LOW)
    @Enumerated(EnumType.STRING)
    private StackLevel stackLevel;
}
