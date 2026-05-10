package com.roddy.domain;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "recommend_job_postings")
public class RecommendJobPosting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommend_job_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "user_id",nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY,optional = false)
    @JoinColumn(name = "job_post_id",nullable = false)
    private JobPosting jobPosting;

    @Column(nullable = false)
    private int matchScore;

    public static RecommendJobPosting create(User user, JobPosting jobPosting, int matchScore) {
        return RecommendJobPosting.builder()
                .user(user)
                .jobPosting(jobPosting)
                .matchScore(matchScore)
                .build();
    }
}
