package com.roddy.domain.notification;

import com.roddy.domain.analysis.service.UserTechStackReader;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.jobposting.entity.JobPosting;
import com.roddy.domain.jobposting.service.MatchRateCalculator;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int JOB_MATCH_THRESHOLD = 70;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserTechStackReader userTechStackReader;

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId) {
        requireUser(userId);
        return responses(userId);
    }

    @Transactional
    public List<NotificationResponse> markRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.NOTIFICATION_NOT_FOUND));
        notification.markRead();
        return responses(userId);
    }

    @Transactional
    public List<NotificationResponse> markAllRead(Long userId) {
        requireUser(userId);
        notificationRepository.findAllByUserIdOrderByIdDesc(userId)
                .forEach(Notification::markRead);
        return responses(userId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createGrowthReport(Long userId, Long reportId) {
        String sourceKey = "report:" + reportId;
        if (notificationRepository.existsByUserIdAndSourceKey(userId, sourceKey)) {
            return;
        }
        User user = requireUser(userId);
        notificationRepository.save(Notification.create(
                user,
                NotificationType.GROWTH_REPORT,
                "성장 리포트가 완성됐어요",
                "새 역량 분석 결과와 달라진 기술 숙련도를 확인해 보세요.",
                null,
                "/reports/%d/detail-analysis".formatted(reportId),
                sourceKey));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createJobMatchNotifications(JobPosting posting) {
        if (posting.getDesiredJob() == null || posting.getTechStacks().isEmpty()) {
            return;
        }
        for (User user : userRepository.findAllByDesiredJobAndDeletedAtIsNull(posting.getDesiredJob())) {
            Map<String, Integer> scores = userTechStackReader.read(user.getId());
            Integer matchRate = MatchRateCalculator.calculate(posting.getTechStacks(), scores);
            if (matchRate == null || matchRate < JOB_MATCH_THRESHOLD) {
                continue;
            }
            String sourceKey = "job:" + posting.getId();
            if (notificationRepository.existsByUserIdAndSourceKey(user.getId(), sourceKey)) {
                continue;
            }
            notificationRepository.save(Notification.create(
                    user,
                    NotificationType.JOB_MATCH,
                    "새 맞춤 공고가 등록됐어요",
                    "%s 공고가 내 기술과 %d%% 일치해요.".formatted(posting.getCompany(), matchRate),
                    posting.getId(),
                    "/jobs/" + posting.getId(),
                    sourceKey));
        }
    }

    private List<NotificationResponse> responses(Long userId) {
        return notificationRepository.findAllByUserIdOrderByIdDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    private User requireUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }
}
