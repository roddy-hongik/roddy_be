package com.roddy.domain.roadmap.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record SavedRoadMapListResponse(
        List<SavedRoadMapResponse> roadmaps,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static SavedRoadMapListResponse from(Page<SavedRoadMapResponse> page) {
        return new SavedRoadMapListResponse(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
