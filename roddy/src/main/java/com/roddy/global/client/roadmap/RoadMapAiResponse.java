package com.roddy.global.client.roadmap;

import java.util.List;

public record RoadMapAiResponse(String title, List<Step> steps) {

    public RoadMapAiResponse {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public record Step(String stage, String goal, List<String> topics, List<String> outputs) {
        public Step {
            topics = topics == null ? List.of() : List.copyOf(topics);
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
        }
    }
}
