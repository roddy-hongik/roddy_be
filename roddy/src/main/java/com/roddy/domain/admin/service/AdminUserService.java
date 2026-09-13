package com.roddy.domain.admin.service;

import com.roddy.domain.admin.dto.AdminUserListResponse;
import com.roddy.domain.admin.dto.AdminUserResponse;
import com.roddy.domain.admin.dto.AdminUserStatus;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.AuthService;
import com.roddy.domain.community.repository.CommunityCommentReportRepository;
import com.roddy.domain.community.repository.CommunityPostReportRepository;
import com.roddy.domain.enums.Role;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final CommunityPostReportRepository communityPostReportRepository;
    private final CommunityCommentReportRepository communityCommentReportRepository;
    private final AuthService authService;

    /** 탈퇴하지 않은 계정을 최근 가입순으로. */
    @Transactional(readOnly = true)
    public AdminUserListResponse getUsers(int page, int size) {
        Page<User> users = userRepository.findAllByDeletedAtIsNull(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        Map<Long, Long> reportCounts = reportCountsOf(users.getContent().stream().map(User::getId).toList());

        return AdminUserListResponse.of(users, users.getContent().stream()
                .map(user -> AdminUserResponse.of(user, reportCounts.getOrDefault(user.getId(), 0L)))
                .toList());
    }

    /**
     * 계정을 정지하거나 푼다.
     *
     * <p>정지하면 저장된 리프레시 토큰을 지운다. 이미 받은 액세스 토큰은 인증 필터가 정지된 계정으로 보고 막는다.
     * 어드민 계정은 정지하지 않는다. 어드민이 모두 정지되면 아무도 풀 수 없기 때문이다.
     */
    @Transactional
    public AdminUserResponse updateStatus(Long userId, AdminUserStatus status) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

        if (status == AdminUserStatus.SUSPENDED) {
            if (user.getRole() == Role.ADMIN) {
                throw new GeneralException(GeneralErrorCode.USER_SUSPEND_FORBIDDEN);
            }
            if (!user.isSuspended()) {
                user.suspend(LocalDateTime.now());
            }
            authService.revokeRefreshToken(user.getId());
        } else {
            user.unsuspend();
        }

        log.info("계정 상태를 바꿨습니다. userId={} status={}", userId, status);
        return AdminUserResponse.of(user, reportCountsOf(List.of(user.getId())).getOrDefault(user.getId(), 0L));
    }

    /** 사용자 id → 그 사람이 쓴 글과 댓글에 들어온 신고 수의 합. 신고가 없으면 빠진다. */
    private Map<Long, Long> reportCountsOf(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> counts = new HashMap<>();
        Stream.concat(
                        communityPostReportRepository.countByAuthorIds(userIds).stream(),
                        communityCommentReportRepository.countByAuthorIds(userIds).stream())
                .forEach(count -> counts.merge(count.getUserId(), count.getReportCount(), Long::sum));
        return counts;
    }
}
