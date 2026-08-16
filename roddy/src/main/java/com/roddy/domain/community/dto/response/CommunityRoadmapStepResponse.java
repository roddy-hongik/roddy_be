package com.roddy.domain.community.dto.response;

import java.util.List;

public record CommunityRoadmapStepResponse(
        String stage,
        String goal,
        List<String> topics,
        List<String> outputs
) {
}
