package com.roddy.domain.admin.controller;

import com.roddy.domain.admin.dto.AdminUserListResponse;
import com.roddy.domain.admin.dto.AdminUserResponse;
import com.roddy.domain.admin.dto.UpdateUserStatusRequest;
import com.roddy.domain.admin.service.AdminUserService;
import com.roddy.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "AdminUser", description = "어드민 사용자 관리 API")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    @Operation(summary = "사용자 목록", description = "탈퇴하지 않은 계정을 최근 가입순으로. 신고 수는 그 사람이 쓴 글과 댓글에 들어온 신고의 합")
    public ApiResponse<AdminUserListResponse> getUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.onSuccess("사용자 목록을 조회했습니다.", adminUserService.getUsers(page, size));
    }

    @PatchMapping("/{userId}/status")
    @Operation(
            summary = "계정 정지·해제",
            description = "정지된 계정은 로그인과 토큰 재발급이 막히고, 이미 받은 토큰으로도 인증되지 않는다. 어드민 계정은 정지할 수 없다")
    public ApiResponse<AdminUserResponse> updateStatus(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.onSuccess("계정 상태를 바꿨습니다.", adminUserService.updateStatus(userId, request.status()));
    }
}
