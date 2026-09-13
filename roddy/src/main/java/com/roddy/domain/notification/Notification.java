package com.roddy.domain.notification;

import com.roddy.domain.BaseEntity;
import com.roddy.domain.auth.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "notifications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_user_source", columnNames = {"user_id", "source_key"}),
        indexes = @Index(name = "idx_notification_user_created", columnList = "user_id, created_at")
)
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "related_job_id")
    private Long relatedJobId;

    @Column(name = "related_path", length = 500)
    private String relatedPath;

    @Column(name = "source_key", nullable = false, length = 100)
    private String sourceKey;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    public static Notification create(
            User user,
            NotificationType type,
            String title,
            String message,
            Long relatedJobId,
            String relatedPath,
            String sourceKey
    ) {
        Notification notification = new Notification();
        notification.user = user;
        notification.type = type;
        notification.title = title;
        notification.message = message;
        notification.relatedJobId = relatedJobId;
        notification.relatedPath = relatedPath;
        notification.sourceKey = sourceKey;
        notification.read = false;
        return notification;
    }

    public void markRead() {
        this.read = true;
    }
}
