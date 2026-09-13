package com.roddy.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import com.roddy.domain.auth.entity.User;

/** 어드민 화면의 계정 상태. 프론트와 소문자 값으로 주고받는다. */
public enum AdminUserStatus {
    ACTIVE("active"),
    SUSPENDED("suspended");

    private final String value;

    AdminUserStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static AdminUserStatus of(User user) {
        return user.isSuspended() ? SUSPENDED : ACTIVE;
    }
}
