package com.roddy.domain.roadmap.dto;

import com.roddy.domain.RoadMap;

import java.time.LocalDateTime;
import java.util.List;

public record SavedRoadMapResponse(
        String id,
        LocalDateTime createdAt,
        String roadmapTitle,
        String targetJob,
        String targetCompany,
        List<String> currentSkills,
        List<String> gapSkills,
        List<RoadMapStepResponse> roadmapSteps
) {
    public static SavedRoadMapResponse from(RoadMap roadMap) {
        return new SavedRoadMapResponse(
                roadMap.getId().toString(),
                roadMap.getCreatedAt(),
                roadMap.getTitle(),
                roadMap.getTargetJob().getDescription(),
                roadMap.getTargetCompany(),
                List.copyOf(roadMap.getCurrentSkills()),
                List.copyOf(roadMap.getGapSkills()),
                roadMap.getSteps().stream().map(RoadMapStepResponse::from).toList()
        );
    }
}
