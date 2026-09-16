package com.roddy.domain.notification;

import com.roddy.domain.analysis.service.UserTechStackReader;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.jobposting.entity.JobPosting;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserTechStackReader userTechStackReader = mock(UserTechStackReader.class);
    private final NotificationService notificationService = new NotificationService(
            notificationRepository, userRepository, userTechStackReader);

    @Test
    void 새_공고가_기준_이상_일치하면_맞춤_알림을_만든다() {
        User user = mock(User.class);
        JobPosting posting = mock(JobPosting.class);
        when(user.getId()).thenReturn(1L);
        when(posting.getId()).thenReturn(20L);
        when(posting.getDesiredJob()).thenReturn(DesiredJob.BACKEND);
        when(posting.getTechStacks()).thenReturn(java.util.Set.of("Java", "Redis"));
        when(posting.getCompany()).thenReturn("토스");
        when(userRepository.findAllByDesiredJobAndDeletedAtIsNull(DesiredJob.BACKEND))
                .thenReturn(List.of(user));
        when(userTechStackReader.read(1L)).thenReturn(Map.of("Java", 90, "Redis", 70));

        notificationService.createJobMatchNotifications(posting);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.JOB_MATCH);
        assertThat(captor.getValue().getMessage()).contains("80%");
        assertThat(captor.getValue().getRelatedPath()).isEqualTo("/jobs/20");
    }
}
