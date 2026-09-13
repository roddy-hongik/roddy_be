package com.roddy.domain.roadmap.dto;

public record SaveRoadMapResponse(boolean saved, SavedRoadMapResponse roadmap, String reason) {

    public static SaveRoadMapResponse saved(SavedRoadMapResponse roadmap) {
        return new SaveRoadMapResponse(true, roadmap, null);
    }

    public static SaveRoadMapResponse duplicate(SavedRoadMapResponse roadmap) {
        return new SaveRoadMapResponse(false, roadmap, "duplicate");
    }
}
