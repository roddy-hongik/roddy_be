package com.roddy.domain.community.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record CommunityPostListItemResponse(
        Long id,
        String type,
        String tag,
        List<String> tags,
        String title,
        String authorName,
        LocalDateTime createdAt,
        int views,
        int likes,
        int commentCount,
        String excerpt,
        String content,
        String roadmapId,
        String roadmapTitle,
        String summary,
        String targetJob,
        String targetCompany,
        List<String> recommendedSkills,
        List<CommunityRoadmapStepResponse> roadmapSteps,
        String description,
        String subtype,
        String company,
        String jobRole,
        String preparationPeriod,
        List<String> techStacks,
        String processSummary,
        String background,
        String preparationProcess,
        String experienceDetail,
        String advice
) {
}
