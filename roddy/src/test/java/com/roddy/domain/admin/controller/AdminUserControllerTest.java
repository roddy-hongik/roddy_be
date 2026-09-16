package com.roddy.domain.admin.controller;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.community.entity.CommunityComment;
import com.roddy.domain.community.entity.CommunityCommentReport;
import com.roddy.domain.community.entity.CommunityPost;
import com.roddy.domain.community.entity.CommunityPostReport;
import com.roddy.domain.community.enums.CommunityJobCategory;
import com.roddy.domain.community.enums.CommunityPostCategory;
import com.roddy.domain.community.repository.CommunityCommentReportRepository;
import com.roddy.domain.community.repository.CommunityCommentRepository;
import com.roddy.domain.community.repository.CommunityPostReportRepository;
import com.roddy.domain.community.repository.CommunityPostRepository;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.global.config.s3.S3Uploader;
import com.roddy.global.jwt.JwtUtil;
import com.roddy.global.security.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AdminUserControllerTest {

    private static final String PASSWORD = "Password1!";

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private CommunityPostRepository communityPostRepository;
    @Autowired private CommunityCommentRepository communityCommentRepository;
    @Autowired private CommunityPostReportRepository communityPostReportRepository;
    @Autowired private CommunityCommentReportRepository communityCommentReportRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    /** 리프레시 토큰과 블랙리스트를 레디스에 둔다. 테스트에서는 흉내만 낸다. */
    @MockitoBean private StringRedisTemplate redisTemplate;
    @MockitoBean private S3Uploader s3Uploader;
    @MockitoBean private SocialAuthService socialAuthService;

    private MockMvc mockMvc;
    private final List<User> users = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        given(redisTemplate.opsForValue()).willReturn(mock(ValueOperations.class));
    }

    @AfterEach
    void tearDown() {
        communityCommentReportRepository.deleteAll();
        communityPostReportRepository.deleteAll();
        communityCommentRepository.deleteAll();
        communityPostRepository.deleteAll();
        userRepository.deleteAll(users);
        users.clear();
    }

    @Test
    void 쓴_글과_댓글에_들어온_신고를_더해_사용자별로_보여준다() throws Exception {
        User admin = saveUser("admin-users@example.com", Role.ADMIN);
        User writer = saveUser("writer@example.com", Role.USER);
        User reporter = saveUser("reporter@example.com", Role.USER);
        User anotherReporter = saveUser("another-reporter@example.com", Role.USER);

        CommunityPost post = communityPostRepository.save(CommunityPost.create(
                writer, CommunityPostCategory.FREE, CommunityJobCategory.B2B, "신고된 글", "본문", List.of()));
        communityPostReportRepository.save(CommunityPostReport.create(post, reporter, null));
        communityPostReportRepository.save(CommunityPostReport.create(post, anotherReporter, null));
        CommunityComment comment = communityCommentRepository.save(CommunityComment.create(post, writer, "신고된 댓글", null));
        communityCommentReportRepository.save(CommunityCommentReport.create(comment, reporter, null));

        mockMvc.perform(get("/api/admin/users").param("size", "100").with(user(new UserDetailsImpl(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.users[?(@.email == 'writer@example.com')].reportCount").value(contains(3)))
                .andExpect(jsonPath("$.result.users[?(@.email == 'writer@example.com')].status").value(contains("active")))
                .andExpect(jsonPath("$.result.users[?(@.email == 'reporter@example.com')].reportCount").value(contains(0)));
    }

    @Test
    void 정지하면_리프레시_토큰을_지우고_이미_받은_토큰으로도_인증되지_않는다() throws Exception {
        User admin = saveUser("admin-suspend@example.com", Role.ADMIN);
        User member = saveUser("member-suspend@example.com", Role.USER);
        String token = bearer(jwtUtil.createAccessToken(member.getEmail(), member.getId()));

        mockMvc.perform(get("/api/notifications").header(JwtUtil.AUTHORIZATION_HEADER, token))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/admin/users/{userId}/status", member.getId())
                        .with(user(new UserDetailsImpl(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"suspended\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("suspended"));
        verify(redisTemplate).delete("RefreshToken:" + member.getId());

        mockMvc.perform(get("/api/notifications").header(JwtUtil.AUTHORIZATION_HEADER, token))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/admin/users/{userId}/status", member.getId())
                        .with(user(new UserDetailsImpl(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"active\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("active"));

        mockMvc.perform(get("/api/notifications").header(JwtUtil.AUTHORIZATION_HEADER, token))
                .andExpect(status().isOk());
    }

    @Test
    void 정지된_계정은_로그인할_수_없다() throws Exception {
        User member = saveUser("suspended-login@example.com", Role.USER);
        member.suspend(LocalDateTime.now());
        userRepository.save(member);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"suspended-login@example.com\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_4032"));
    }

    @Test
    void 로그인하면_최근_활동일로_남는다() throws Exception {
        User admin = saveUser("admin-active@example.com", Role.ADMIN);
        User member = saveUser("login-active@example.com", Role.USER);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"login-active@example.com\", \"password\": \"%s\"}".formatted(PASSWORD)))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(member.getId()).orElseThrow().getLastLoginAt()).isNotNull();
        mockMvc.perform(get("/api/admin/users").param("size", "100").with(user(new UserDetailsImpl(admin))))
                .andExpect(jsonPath("$.result.users[?(@.email == 'login-active@example.com')].lastActiveAt").value(hasItem(notNullValue())));
    }

    @Test
    void 어드민_계정은_정지할_수_없다() throws Exception {
        User admin = saveUser("admin-self@example.com", Role.ADMIN);
        User otherAdmin = saveUser("admin-other@example.com", Role.ADMIN);

        mockMvc.perform(patch("/api/admin/users/{userId}/status", otherAdmin.getId())
                        .with(user(new UserDetailsImpl(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"suspended\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_4001"));
    }

    @Test
    void 어드민이_아니면_사용자를_관리할_수_없다() throws Exception {
        User member = saveUser("member-admin-api@example.com", Role.USER);

        mockMvc.perform(get("/api/admin/users").with(user(new UserDetailsImpl(member))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    private String bearer(String token) {
        return token.startsWith(JwtUtil.BEARER_PREFIX) ? token : JwtUtil.BEARER_PREFIX + token;
    }

    private User saveUser(String email, Role role) {
        User saved = userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .socialType(SocialType.LOCAL)
                .nickname(email.substring(0, email.indexOf('@')))
                .username(email.substring(0, email.indexOf('@')))
                .role(role)
                .build());
        users.add(saved);
        return saved;
    }
}
