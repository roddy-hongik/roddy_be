package com.roddy.domain.coverletter.entity;

import com.roddy.domain.BaseEntity;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.jobposting.entity.JobPosting;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "cover_letters", indexes = @Index(name = "idx_cover_letter_user_updated", columnList = "user_id, updated_at"))
public class CoverLetter extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cover_letter_id")
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id")
    private JobPosting jobPosting;
    @Column(nullable = false, length = 255)
    private String title;
    @Version
    @Column(nullable = false)
    private long version;
    @ElementCollection
    @CollectionTable(name = "cover_letter_answers", joinColumns = @JoinColumn(name = "cover_letter_id"))
    @OrderColumn(name = "answer_order")
    private List<CoverLetterAnswer> answers = new ArrayList<>();

    public static CoverLetter create(User user, String title, JobPosting jobPosting, List<CoverLetterAnswer> answers) {
        CoverLetter letter = new CoverLetter();
        letter.user = user;
        letter.replace(title, jobPosting, answers);
        return letter;
    }

    public void replace(String title, JobPosting jobPosting, List<CoverLetterAnswer> answers) {
        this.title = title.trim();
        this.jobPosting = jobPosting;
        this.answers.clear();
        this.answers.addAll(answers);
    }
}
