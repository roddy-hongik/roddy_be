package com.roddy.domain.admin.dto;

import com.roddy.domain.auth.entity.User;

import java.time.LocalDateTime;

/**
 * 어드민 사용자 목록의 한 줄.
 *
 * @param reportCount  그 사람이 쓴 글과 댓글에 들어온 신고 수의 합
 * @param lastActiveAt 마지막으로 로그인했거나 토큰을 재발급받은 시각. 기록이 없으면 null
 */
public record AdminUserResponse(
        Long id,
        String nickname,
        String email,
        AdminUserStatus status,
        long reportCount,
        LocalDateTime joinedAt,
        LocalDateTime lastActiveAt
) {

    public static AdminUserResponse of(User user, long reportCount) {
        return new AdminUserResponse(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                AdminUserStatus.of(user),
                reportCount,
                user.getCreatedAt(),
                user.getLastLoginAt());
    }
}
