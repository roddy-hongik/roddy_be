package com.roddy.domain.notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        String id,
        String type,
        String title,
        String message,
        LocalDateTime createdAt,
        boolean isRead,
        String relatedJobId,
        String relatedPath
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId().toString(),
                notification.getType().value(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getCreatedAt(),
                notification.isRead(),
                notification.getRelatedJobId() == null ? null : notification.getRelatedJobId().toString(),
                notification.getRelatedPath());
    }
}
