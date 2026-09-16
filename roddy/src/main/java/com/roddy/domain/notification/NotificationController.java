package com.roddy.domain.notification;

import com.roddy.global.apiPayload.ApiResponse;
import com.roddy.global.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "내 알림 목록")
    public ApiResponse<List<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ApiResponse.onSuccess(
                "알림을 조회했습니다.",
                notificationService.getNotifications(userDetails.getUser().getId()));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "알림 한 건 읽음 처리")
    public ApiResponse<List<NotificationResponse>> markRead(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @PathVariable Long notificationId) {
        return ApiResponse.onSuccess(
                "알림을 읽음 처리했습니다.",
                notificationService.markRead(userDetails.getUser().getId(), notificationId));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "모든 알림 읽음 처리")
    public ApiResponse<List<NotificationResponse>> markAllRead(
            @AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ApiResponse.onSuccess(
                "모든 알림을 읽음 처리했습니다.",
                notificationService.markAllRead(userDetails.getUser().getId()));
    }
}
