package com.roddy.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "job_competencies")
public class JobCompetency extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_analysis_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id")
    private JobPosting jobPosting;

    // Field1 ~ 6에 해당하는 역량 기준 점수들
    private Integer requiredDataModeling;
    private Integer requiredArchitecture;
    private Integer requiredScalability;
    private Integer requiredStability;
    private Integer requiredDevops;
    private Integer requiredMonitoring;
}