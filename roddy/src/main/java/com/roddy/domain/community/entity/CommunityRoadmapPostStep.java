package com.roddy.domain.community.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(name = "community_roadmap_post_steps")
public class CommunityRoadmapPostStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "community_roadmap_post_step_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "community_roadmap_post_detail_id", nullable = false)
    private CommunityRoadmapPostDetail roadmapDetail;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private String stage;

    @Column(nullable = false)
    private String goal;

    @ElementCollection
    @CollectionTable(
            name = "community_roadmap_post_step_topics",
            joinColumns = @JoinColumn(name = "community_roadmap_post_step_id")
    )
    @Column(name = "topic", nullable = false)
    @Builder.Default
    private List<String> topics = new ArrayList<>();

    @ElementCollection
    @CollectionTable(
            name = "community_roadmap_post_step_outputs",
            joinColumns = @JoinColumn(name = "community_roadmap_post_step_id")
    )
    @Column(name = "output", nullable = false)
    @Builder.Default
    private List<String> outputs = new ArrayList<>();

    public static CommunityRoadmapPostStep create(
            CommunityRoadmapPostDetail roadmapDetail,
            int displayOrder,
            String stage,
            String goal,
            List<String> topics,
            List<String> outputs
    ) {
        return CommunityRoadmapPostStep.builder()
                .roadmapDetail(roadmapDetail)
                .displayOrder(displayOrder)
                .stage(stage == null ? "" : stage.trim())
                .goal(goal == null ? "" : goal.trim())
                .topics(normalizeValues(topics))
                .outputs(normalizeValues(outputs))
                .build();
    }

    private static List<String> normalizeValues(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}
