package com.roddy.domain.analysis.entity;

import com.roddy.domain.BaseEntity;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.enums.StackLevel;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import lombok.*;

/**
 * 리포트 한 건이 찾아낸 기술 하나.
 *
 * <p>같은 기술도 리포트마다 따로 남긴다. 지난 리포트를 다시 열었을 때 그때의 기술과 숙련도가 보여야
 * 하기 때문이다. 사용자의 지금 기술은 가장 최근에 끝난 리포트에 딸린 행이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "user_stacks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_stack_report", columnNames = {"analysis_report_id", "stack_detail_id"}),
        indexes = @Index(name = "idx_user_stack_user", columnList = "user_id")
)
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
        return create(user, stackDetail, analysisReport, null, score, description);
    }

    public static UserStack create(User user, StackDetail stackDetail,
                                   AnalysisReport analysisReport, StackLevel stackLevel,
                                   int score, String description) {
        return UserStack.builder()
                .user(user)
                .stackDetail(stackDetail)
                .analysisReport(analysisReport)
                .stackLevel(stackLevel)
                .score(score)
                .description(description)
                .build();
    }

    public void update(int score, String description) {
        this.score = score;
        this.description = description;
    }
}
