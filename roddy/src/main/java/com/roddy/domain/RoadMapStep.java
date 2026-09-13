package com.roddy.domain;

import jakarta.persistence.*;
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
@Table(name = "learning_roadmap_steps")
public class RoadMapStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "roadmap_step_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_id", nullable = false)
    private RoadMap roadMap;

    @Column(nullable = false, length = 30)
    private String stage;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String goal;

    @ElementCollection
    @CollectionTable(name = "learning_roadmap_step_topics", joinColumns = @JoinColumn(name = "roadmap_step_id"))
    @Column(name = "topic", nullable = false, length = 500)
    @OrderColumn(name = "topic_order")
    @Builder.Default
    private List<String> topics = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "learning_roadmap_step_outputs", joinColumns = @JoinColumn(name = "roadmap_step_id"))
    @Column(name = "output_text", nullable = false, length = 500)
    @OrderColumn(name = "output_order")
    @Builder.Default
    private List<String> outputs = new ArrayList<>();

    static RoadMapStep create(RoadMap roadMap, String stage, String goal,
                              List<String> topics, List<String> outputs) {
        return RoadMapStep.builder()
                .roadMap(roadMap)
                .stage(stage)
                .goal(goal)
                .topics(new ArrayList<>(topics))
                .outputs(new ArrayList<>(outputs))
                .build();
    }
}
