package com.roddy.domain.community.dto.response;

import java.util.List;

public record CommunityPostDetailResponse(
        Long id,
        String type,
        String tag,
        List<String> tags,
        String title,
        String content,
        String authorName,
        java.time.LocalDateTime createdAt,
        int views,
        int likes,
        int commentCount,
        boolean liked,
        String excerpt,
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
        String advice,
        List<String> imageUrls,
        List<CommunityCommentResponse> comments
) {
}
