package com.roddy.domain;

import com.roddy.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "job_bookmarks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_job_bookmark_user_job", columnNames = {"user_id", "job_posting_id"})
})
public class JobBookmark extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_bookmark_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    public static JobBookmark create(User user, JobPosting jobPosting) {
        return JobBookmark.builder()
                .user(user)
                .jobPosting(jobPosting)
                .build();
    }
}