package com.roddy.domain.analysis.controller;

import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.analysis.enums.AnalysisStatus;
import com.roddy.domain.analysis.repository.AnalysisReportRepository;
import com.roddy.domain.analysis.repository.StackDetailRepository;
import com.roddy.domain.analysis.repository.UserStackRepository;
import com.roddy.domain.analysis.service.AnalysisRunner;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.global.config.s3.S3ObjectUrlService;
import com.roddy.global.config.s3.S3Uploader;
import com.roddy.global.security.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AnalysisControllerTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private AnalysisReportRepository analysisReportRepository;

    @Autowired
    private UserStackRepository userStackRepository;

    @Autowired
    private StackDetailRepository stackDetailRepository;

    @Autowired
    private UserRepository userRepository;

    /** 실제로 AI 서버를 부르지 않는다. 분석이 시작됐는지만 본다. */
    @MockitoBean
    private AnalysisRunner analysisRunner;

    @MockitoBean
    private S3Uploader s3Uploader;

    @MockitoBean
    private S3ObjectUrlService s3ObjectUrlService;

    @MockitoBean
    private SocialAuthService socialAuthService;

    private MockMvc mockMvc;

    private final List<User> createdUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        clear();
    }

    @AfterEach
    void tearDown() {
        clear();
        userRepository.deleteAll(createdUsers);
        createdUsers.clear();
    }

    @Test
    @DisplayName("분석을 요청하면 진행 중 상태를 바로 돌려준다")
    void startsAnalysis() throws Exception {
        User user = saveUser("start@example.com");

        mockMvc.perform(post("/api/analysis/me").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").isNumber())
                .andExpect(jsonPath("$.result.status").value("PENDING"))
                // 분석은 수십 초가 걸리므로 내용은 아직 비어 있다.
                .andExpect(jsonPath("$.result.title").doesNotExist())
                .andExpect(jsonPath("$.result.stacks.length()").value(0));

        verify(analysisRunner, times(1)).run(eq(user.getId()), anyLong());
    }

    @Test
    @DisplayName("이미 분석 중이면 다시 돌리지 않는다")
    void doesNotStartTwice() throws Exception {
        User user = saveUser("twice@example.com");

        mockMvc.perform(post("/api/analysis/me").with(user(new UserDetailsImpl(user))));
        mockMvc.perform(post("/api/analysis/me").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("PENDING"));

        // 같은 사용자를 두 번 돌리면 LLM 비용만 두 배가 된다.
        verify(analysisRunner, times(1)).run(eq(user.getId()), anyLong());
        assertThat(analysisReportRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("분석이 끝난 뒤 다시 요청하면 지난 리포트는 두고 새 리포트로 분석한다")
    void startsNewReportAfterCompletion() throws Exception {
        User user = saveUser("again@example.com");
        mockMvc.perform(post("/api/analysis/me").with(user(new UserDetailsImpl(user))));
        AnalysisReport first = analysisReportRepository.findFirstByUserIdOrderByIdDesc(user.getId()).orElseThrow();
        first.complete("첫 분석", 60, "요약", "깃허브 분석", "포트폴리오 분석", LocalDateTime.now());
        analysisReportRepository.save(first);

        mockMvc.perform(post("/api/analysis/me").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("PENDING"));

        assertThat(analysisReportRepository.count()).isEqualTo(2);
        assertThat(analysisReportRepository.findById(first.getId()))
                .hasValueSatisfying(report -> assertThat(report.getStatus()).isEqualTo(AnalysisStatus.COMPLETED));
        verify(analysisRunner, times(2)).run(eq(user.getId()), anyLong());
    }

    @Test
    @DisplayName("아직 분석하지 않은 사용자는 빈 리포트를 받는다")
    void returnsEmptyReportBeforeAnalysis() throws Exception {
        User user = saveUser("empty@example.com");

        mockMvc.perform(get("/api/analysis/me").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").doesNotExist())
                .andExpect(jsonPath("$.result.status").doesNotExist())
                .andExpect(jsonPath("$.result.stacks.length()").value(0));

        verify(analysisRunner, never()).run(anyLong(), anyLong());
    }

    @Test
    @DisplayName("로그인하지 않으면 분석을 요청할 수 없다")
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(post("/api/analysis/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analysis/me")).andExpect(status().isUnauthorized());
    }

    private void clear() {
        userStackRepository.deleteAll();
        analysisReportRepository.deleteAll();
        stackDetailRepository.deleteAll();
    }

    private User saveUser(String email) {
        User saved = userRepository.save(
                User.builder()
                        .email(email)
                        .password("encoded-password")
                        .socialType(SocialType.LOCAL)
                        .nickname("분석요청유저")
                        .username("분석요청유저")
                        .role(Role.USER)
                        .build()
        );
        createdUsers.add(saved);
        return saved;
    }
}
