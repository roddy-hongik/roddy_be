package com.roddy.domain;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.enums.DesiredJob;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

/**
 * 생성 당시의 분석 요약과 학습 단계를 함께 보존하는 로드맵.
 *
 * <p>저장한 로드맵 목록은 한 페이지를 한꺼번에 그리므로, 컬렉션을 로드맵마다 따로 읽지 않고 묶어서 읽는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "learning_roadmaps",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_roadmap_user_fingerprint", columnNames = {"user_id", "fingerprint"})
)
public class RoadMap extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "roadmap_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private DesiredJob targetJob;

    private String targetCompany;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @ElementCollection
    @CollectionTable(name = "learning_roadmap_current_skills", joinColumns = @JoinColumn(name = "roadmap_id"))
    @Column(name = "skill", nullable = false, length = 100)
    @OrderColumn(name = "skill_order")
    @BatchSize(size = 100)
    @Builder.Default
    private List<String> currentSkills = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "learning_roadmap_gap_skills", joinColumns = @JoinColumn(name = "roadmap_id"))
    @Column(name = "skill", nullable = false, length = 100)
    @OrderColumn(name = "skill_order")
    @BatchSize(size = 100)
    @Builder.Default
    private List<String> gapSkills = new ArrayList<>();

    @OneToMany(mappedBy = "roadMap", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "step_order")
    @BatchSize(size = 100)
    @Builder.Default
    private List<RoadMapStep> steps = new ArrayList<>();

    public static RoadMap create(User user, String title, DesiredJob targetJob, String targetCompany,
                                 List<String> currentSkills, List<String> gapSkills, String fingerprint) {
        return RoadMap.builder()
                .user(user)
                .title(title)
                .targetJob(targetJob)
                .targetCompany(targetCompany)
                .currentSkills(new ArrayList<>(currentSkills))
                .gapSkills(new ArrayList<>(gapSkills))
                .fingerprint(fingerprint)
                .steps(new ArrayList<>())
                .build();
    }

    public void addStep(String stage, String goal, List<String> topics, List<String> outputs) {
        steps.add(RoadMapStep.create(this, stage, goal, topics, outputs));
    }
}
