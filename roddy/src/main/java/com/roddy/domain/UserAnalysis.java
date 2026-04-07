package com.roddy.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "user_analyses")
public class UserAnalysis extends BaseEntity { // createdAt을 lastAnalyzedAt 대신 사용 가능

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_analysis_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private Integer dataModelingScore;
    private Integer architectureScore;
    private Integer scalabilityScore;
    private Integer stabilityScore;
    private Integer devopsScore;
    private Integer monitoringScore;
}