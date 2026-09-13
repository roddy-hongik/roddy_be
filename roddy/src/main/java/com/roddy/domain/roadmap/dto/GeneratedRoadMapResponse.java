package com.roddy.domain.roadmap.dto;

import com.roddy.global.client.roadmap.RoadMapAiResponse;

import java.util.List;

public record GeneratedRoadMapResponse(String title, List<RoadMapStepResponse> steps) {

    public static GeneratedRoadMapResponse from(RoadMapAiResponse response) {
        return new GeneratedRoadMapResponse(
                response.title(),
                response.steps().stream()
                        .map(step -> new RoadMapStepResponse(
                                step.stage(), step.goal(), step.topics(), step.outputs()))
                        .toList()
        );
    }
}
