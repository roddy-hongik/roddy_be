package com.roddy.domain.roadmap.dto;

import com.roddy.domain.RoadMapStep;

import java.util.List;

public record RoadMapStepResponse(String stage, String goal, List<String> topics, List<String> outputs) {

    public static RoadMapStepResponse from(RoadMapStep step) {
        return new RoadMapStepResponse(step.getStage(), step.getGoal(), step.getTopics(), step.getOutputs());
    }
}
