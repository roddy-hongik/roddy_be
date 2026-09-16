package com.roddy.domain.admin.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull AdminUserStatus status) {
}
