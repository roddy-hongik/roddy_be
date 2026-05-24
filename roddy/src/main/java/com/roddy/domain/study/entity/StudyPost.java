package com.roddy.domain.study.entity;

import com.roddy.domain.BaseEntity;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.study.enums.StudyMode;
import com.roddy.domain.study.enums.StudyRecruitStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "study_posts")
public class StudyPost extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "study_post_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StudyMode mode;

    private String location;

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int applicantCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StudyRecruitStatus status;

    @OneToMany(mappedBy = "studyPost", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StudyApplication> applications = new ArrayList<>();

    public static StudyPost create(
            User author,
            String title,
            String content,
            StudyMode mode,
            String location,
            LocalDateTime scheduledAt,
            int capacity
    ) {
        return StudyPost.builder()
                .author(author)
                .title(title.trim())
                .content(content.trim())
                .mode(mode)
                .location(normalizeLocation(location))
                .scheduledAt(scheduledAt)
                .capacity(capacity)
                .applicantCount(0)
                .status(StudyRecruitStatus.RECRUITING)
                .applications(new ArrayList<>())
                .build();
    }

    public void increaseApplicantCount() {
        this.applicantCount++;
    }

    public void decreaseApplicantCount() {
        if (this.applicantCount > 0) {
            this.applicantCount--;
        }
    }

    public void close() {
        this.status = StudyRecruitStatus.CLOSED;
    }

    public boolean isClosed() {
        return this.status == StudyRecruitStatus.CLOSED;
    }

    public boolean isFull() {
        return this.applicantCount >= this.capacity;
    }

    public boolean isAuthor(Long userId) {
        return this.author != null && this.author.getId().equals(userId);
    }

    private static String normalizeLocation(String location) {
        if (location == null) {
            return null;
        }
        String trimmed = location.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
