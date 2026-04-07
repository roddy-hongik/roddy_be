package com.roddy.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "job_postings")
public class JobPosting extends BaseEntity{

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long id;

    private String title;

    private String recruitField; // 채용 분야

    @Column(columnDefinition = "TEXT")
    private String content; // 채용 본문 내용

    // 하나의 기업은 여러 채용공고를 가질 수 있으므로 N:1 관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private String jobCategory; // 인턴/신입/경력직
}