package com.roddy.domain;

import com.roddy.domain.enums.StudyApplyStatus;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "study_applies", uniqueConstraints = {
        @UniqueConstraint(name = "uk_study_apply_user_study", columnNames = {"user_id", "study_id"})
})
public class StudyApply extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "study_apply_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "study_id", nullable = false)
    private Study study;

    // 대기중, 승인됨, 거절됨 등 상태 관리
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StudyApplyStatus status;

    public static StudyApply create(User user, Study study) {
        return StudyApply.builder()
                .user(user)
                .study(study)
                .status(StudyApplyStatus.PENDING)  // 초기값 대기중
                .build();
    }

    public void approve() {
        if (this.status != StudyApplyStatus.PENDING) {
            throw new IllegalStateException("대기중인 신청만 승인할 수 있습니다.");
        }
        this.status = StudyApplyStatus.APPROVED;
    }

    public void reject() {
        if (this.status != StudyApplyStatus.PENDING) {
            throw new IllegalStateException("대기중인 신청만 거절할 수 있습니다.");
        }
        this.status = StudyApplyStatus.REJECTED;
    }
}