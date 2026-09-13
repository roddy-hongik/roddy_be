package com.roddy.domain.notification;

public enum NotificationType {
    JOB_MATCH("job_match"),
    GROWTH_REPORT("growth_report");

    private final String value;

    NotificationType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
