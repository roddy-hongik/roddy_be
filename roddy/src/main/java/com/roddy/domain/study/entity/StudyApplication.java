package com.roddy.domain.study.entity;

import com.roddy.domain.BaseEntity;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.study.enums.StudyApplicationStatus;
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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "study_applications",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_study_application_post_applicant",
                        columnNames = {"study_post_id", "applicant_id"}
                )
        }
)
public class StudyApplication extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "study_application_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "study_post_id", nullable = false)
    private StudyPost studyPost;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StudyApplicationStatus status;

    public static StudyApplication create(StudyPost studyPost, User applicant) {
        return StudyApplication.builder()
                .studyPost(studyPost)
                .applicant(applicant)
                .status(StudyApplicationStatus.APPLIED)
                .build();
    }

    public void apply() {
        this.status = StudyApplicationStatus.APPLIED;
    }

    public void cancel() {
        this.status = StudyApplicationStatus.CANCELED;
    }

    public boolean isApplied() {
        return this.status == StudyApplicationStatus.APPLIED;
    }

    public boolean isCanceled() {
        return this.status == StudyApplicationStatus.CANCELED;
    }
}
