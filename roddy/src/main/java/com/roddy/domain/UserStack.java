package com.roddy.domain;

import com.roddy.domain.enums.StackLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_stacks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_stack", columnNames = {"user_id", "stack_id"})
})
public class UserStack extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_stack_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stack_id", nullable = false)
    private Stack stack;

    // 숙련도
    @Enumerated(EnumType.STRING)
    private StackLevel stackLevel;
}
