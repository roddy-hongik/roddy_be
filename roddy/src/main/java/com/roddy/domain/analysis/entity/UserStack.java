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

    /** 이 기술이 속한 평가 축의 code. 축을 정하지 않은 직무거나 AI 가 축을 고르지 못했으면 비어 있다. */
    @Column(length = 50)
    private String categoryCode;

    /** 깃허브 저장소에서 근거를 찾았는지. 프론트는 깃허브에서 찾은 기술과 이력서에서 찾은 기술을 나눠 보여준다. */
    @Column(nullable = false)
    private boolean foundInGithub;

    /** 포트폴리오에서 근거를 찾았는지. */
    @Column(nullable = false)
    private boolean foundInPortfolio;

    public static UserStack create(User user, StackDetail stackDetail,
                                   AnalysisReport analysisReport,
                                   int score, String description) {
        return create(user, stackDetail, analysisReport, null, score, description, null, false, false);
    }

    public static UserStack create(User user, StackDetail stackDetail,
                                   AnalysisReport analysisReport, StackLevel stackLevel,
                                   int score, String description,
                                   String categoryCode, boolean foundInGithub, boolean foundInPortfolio) {
        return UserStack.builder()
                .user(user)
                .stackDetail(stackDetail)
                .analysisReport(analysisReport)
                .stackLevel(stackLevel)
                .score(score)
                .description(description)
                .categoryCode(categoryCode)
                .foundInGithub(foundInGithub)
                .foundInPortfolio(foundInPortfolio)
                .build();
    }

    public void update(int score, String description) {
        this.score = score;
        this.description = description;
    }
}
