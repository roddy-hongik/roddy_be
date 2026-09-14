package com.roddy.domain.admin.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record AdminUserListResponse(
        List<AdminUserResponse> users,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static AdminUserListResponse of(Page<?> page, List<AdminUserResponse> users) {
        return new AdminUserListResponse(
                users, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
