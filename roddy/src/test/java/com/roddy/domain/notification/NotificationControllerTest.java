package com.roddy.domain.notification;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.global.config.s3.S3Uploader;
import com.roddy.global.security.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;

    @MockitoBean private S3Uploader s3Uploader;
    @MockitoBean private SocialAuthService socialAuthService;

    private MockMvc mockMvc;
    private final List<User> users = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        notificationRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        userRepository.deleteAll(users);
        users.clear();
    }

    @Test
    void 내_알림을_최신순으로_조회하고_한_건을_읽는다() throws Exception {
        User owner = saveUser("notification-owner@example.com");
        save(owner, NotificationType.GROWTH_REPORT, "report:1", null);
        Notification latest = save(owner, NotificationType.JOB_MATCH, "job:2", 2L);

        mockMvc.perform(get("/api/notifications").with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].id").value(latest.getId().toString()))
                .andExpect(jsonPath("$.result[0].type").value("job_match"))
                .andExpect(jsonPath("$.result[0].relatedJobId").value("2"))
                .andExpect(jsonPath("$.result[0].isRead").value(false));

        mockMvc.perform(patch("/api/notifications/{id}/read", latest.getId())
                        .with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[0].isRead").value(true));
    }

    @Test
    void 다른_사용자의_알림은_읽을_수_없다() throws Exception {
        User owner = saveUser("notification-owner-2@example.com");
        User other = saveUser("notification-other@example.com");
        Notification notification = save(owner, NotificationType.GROWTH_REPORT, "report:2", null);

        mockMvc.perform(patch("/api/notifications/{id}/read", notification.getId())
                        .with(user(new UserDetailsImpl(other))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_4041"));
    }

    @Test
    void 모든_알림을_읽음_처리하고_분석_완료_알림은_중복_생성하지_않는다() throws Exception {
        User owner = saveUser("notification-all@example.com");
        notificationService.createGrowthReport(owner.getId(), 10L);
        notificationService.createGrowthReport(owner.getId(), 10L);
        save(owner, NotificationType.JOB_MATCH, "job:3", 3L);

        mockMvc.perform(patch("/api/notifications/read-all")
                        .with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].isRead").value(true))
                .andExpect(jsonPath("$.result[1].isRead").value(true));
    }

    @Test
    void 비로그인_사용자는_알림을_볼_수_없다() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
    }

    private User saveUser(String email) {
        User user = userRepository.save(User.builder()
                .email(email)
                .password("encoded-password")
                .socialType(SocialType.LOCAL)
                .nickname("알림유저")
                .username("알림유저")
                .role(Role.USER)
                .build());
        users.add(user);
        return user;
    }

    private Notification save(User user, NotificationType type, String sourceKey, Long jobId) {
        return notificationRepository.save(Notification.create(
                user, type, "제목", "내용", jobId,
                jobId == null ? "/reports/1/detail-analysis" : "/jobs/" + jobId,
                sourceKey));
    }
}
