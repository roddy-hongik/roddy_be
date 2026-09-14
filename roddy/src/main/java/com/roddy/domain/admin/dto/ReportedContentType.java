package com.roddy.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ReportedContentType {
    POST("post"),
    COMMENT("comment");

    private final String value;

    ReportedContentType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
