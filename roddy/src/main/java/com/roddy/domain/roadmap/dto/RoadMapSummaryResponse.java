package com.roddy.domain.roadmap.dto;

import java.util.List;

public record RoadMapSummaryResponse(
        List<String> currentSkills,
        List<String> gapSkills,
        String targetJob,
        String targetCompany
) {
}
