package com.roddy.domain;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.enums.StackLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "user_stacks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_stack", columnNames = {"user_id", "stack_detail_id"})
})
public class UserStack extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_stack_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stack_detail_id", nullable = false)
    private StackDetail stackDetail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_report_id", nullable = false)
    private AnalysisReport analysisReport;

    // 숙련도
    @Enumerated(EnumType.STRING)
    private StackLevel stackLevel;

    @Column(nullable = false)
    private int score;


    //이유 설명

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    public static UserStack create(User user, StackDetail stackDetail,
                                   AnalysisReport analysisReport,
                                   int score, String description) {
        return UserStack.builder()
                .user(user)
                .stackDetail(stackDetail)
                .analysisReport(analysisReport)
                .score(score)
                .description(description)
                .build();
    }

    public void update(int score, String description) {
        this.score = score;
        this.description = description;
    }
}
