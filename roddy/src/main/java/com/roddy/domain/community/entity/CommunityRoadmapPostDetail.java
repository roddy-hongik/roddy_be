package com.roddy.domain.community.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "community_roadmap_post_details")
public class CommunityRoadmapPostDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_roadmap_post_detail_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_post_id", nullable = false, unique = true)
    private CommunityPost post;

    private String roadmapId;

    @Column(nullable = false)
    private String roadmapTitle;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(nullable = false)
    private String targetJob;

    private String targetCompany;

    @ElementCollection
    @CollectionTable(
            name = "community_roadmap_post_recommended_skills",
            joinColumns = @JoinColumn(name = "community_roadmap_post_detail_id")
    )
    @Column(name = "recommended_skill", nullable = false)
    @Builder.Default
    private Set<String> recommendedSkills = new LinkedHashSet<>();

    @OneToMany(mappedBy = "roadmapDetail", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder asc")
    @Builder.Default
    private List<CommunityRoadmapPostStep> roadmapSteps = new ArrayList<>();

    public static CommunityRoadmapPostDetail create(
            CommunityPost post,
            String roadmapId,
            String roadmapTitle,
            String summary,
            String description,
            String targetJob,
            String targetCompany,
            List<String> recommendedSkills
    ) {
        return CommunityRoadmapPostDetail.builder()
                .post(post)
                .roadmapId(normalizeNullable(roadmapId))
                .roadmapTitle(requireText(roadmapTitle))
                .summary(defaultIfBlank(summary, ""))
                .description(defaultIfBlank(description, ""))
                .targetJob(requireText(targetJob))
                .targetCompany(normalizeNullable(targetCompany))
                .recommendedSkills(normalizeValues(recommendedSkills))
                .roadmapSteps(new ArrayList<>())
                .build();
    }

    public void replaceSteps(List<CommunityRoadmapPostStep> steps) {
        this.roadmapSteps.clear();
        if (steps != null) {
            steps.forEach(this::addStep);
        }
    }

    public void addStep(CommunityRoadmapPostStep step) {
        this.roadmapSteps.add(step);
    }

    private static String requireText(String value) {
        String trimmed = normalizeNullable(value);
        if (trimmed == null) {
            throw new IllegalArgumentException("필수 로드맵 상세 값이 누락되었습니다.");
        }
        return trimmed;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        String normalized = normalizeNullable(value);
        return normalized == null ? defaultValue : normalized;
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static Set<String> normalizeValues(List<String> values) {
        if (values == null) {
            return new LinkedHashSet<>();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
