package com.roddy.domain.jobposting.controller;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.domain.jobposting.dto.IngestSummary;
import com.roddy.domain.jobposting.entity.CrawlRun;
import com.roddy.domain.jobposting.repository.CrawlRunRepository;
import com.roddy.domain.jobposting.service.JobPostingCrawlLauncher;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AdminCrawlingControllerTest {

    private static final LocalDateTime TODAY_MORNING = LocalDate.now().atTime(4, 0);
    private static final LocalDateTime YESTERDAY = LocalDate.now().minusDays(1).atTime(4, 0);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private CrawlRunRepository crawlRunRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private S3Uploader s3Uploader;

    @MockitoBean
    private SocialAuthService socialAuthService;

    /** 실제 채용 사이트로 요청이 나가지 않도록 수집 시작은 가짜로 둔다. */
    @MockitoBean
    private JobPostingCrawlLauncher crawlLauncher;

    private MockMvc mockMvc;

    private final List<User> createdUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        crawlRunRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        crawlRunRepository.deleteAll();
        userRepository.deleteAll(createdUsers);
        createdUsers.clear();
    }

    @Test
    @DisplayName("오늘 수집 결과를 회사별로 합쳐서 보여준다")
    void summarizesTodayRuns() throws Exception {
        crawlRunRepository.save(CrawlRun.completed("kakao", new IngestSummary(50, 5, 3, 42, 1, 0),
                0, List.of(), TODAY_MORNING, TODAY_MORNING.plusMinutes(2)));
        crawlRunRepository.save(CrawlRun.completed("naver", new IngestSummary(30, 30, 0, 0, 0, 2),
                0, List.of("필수 필드 'title' 가 비어 있습니다: 2/30"), TODAY_MORNING, TODAY_MORNING.plusMinutes(1)));

        mockMvc.perform(get("/api/admin/crawling/dashboard").with(user(adminDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalCollectedToday").value(80))
                .andExpect(jsonPath("$.result.successCount").value(80))
                .andExpect(jsonPath("$.result.failCount").value(2))
                .andExpect(jsonPath("$.result.companies[?(@.id == 'kakao')].status").value("healthy"))
                .andExpect(jsonPath("$.result.companies[?(@.id == 'naver')].status").value("warning"))
                .andExpect(jsonPath("$.result.companies[?(@.id == 'kakao')].collectedToday").value(50));
    }

    @Test
    @DisplayName("수집이 실패한 회사를 맨 위로 올리고 개수도 알려준다")
    void liftsBrokenCompaniesToTop() throws Exception {
        crawlRunRepository.save(CrawlRun.completed("kakao", new IngestSummary(50, 0, 0, 50, 0, 0),
                0, List.of(), TODAY_MORNING, TODAY_MORNING.plusMinutes(2)));
        crawlRunRepository.save(CrawlRun.failed("toss", "RestClientException: 503",
                TODAY_MORNING, TODAY_MORNING.plusSeconds(20)));

        mockMvc.perform(get("/api/admin/crawling/dashboard").with(user(adminDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.companies[0].id").value("toss"))
                .andExpect(jsonPath("$.result.companies[0].status").value("error"))
                .andExpect(jsonPath("$.result.errorCount").value(1));
    }

    @Test
    @DisplayName("명세에 있지만 한 번도 돌지 않은 회사도 빠뜨리지 않는다")
    void showsCompaniesWithoutAnyRun() throws Exception {
        mockMvc.perform(get("/api/admin/crawling/dashboard").with(user(adminDetails())))
                .andExpect(status().isOk())
                // 수집 이력이 없으면 전부 주의 상태로 보인다. 수집이 멈춘 것을 알아채기 위함이다.
                .andExpect(jsonPath("$.result.companies[0].lastCrawledAt").doesNotExist())
                .andExpect(jsonPath("$.result.companies[0].status").value("warning"))
                .andExpect(jsonPath("$.result.totalCollectedToday").value(0))
                .andExpect(jsonPath("$.result.lastCrawledAt").doesNotExist());
    }

    @Test
    @DisplayName("어제 수집은 오늘 합계에 넣지 않되 마지막 시각으로는 남긴다")
    void separatesTodayFromLastRun() throws Exception {
        crawlRunRepository.save(CrawlRun.completed("kakao", new IngestSummary(50, 5, 3, 42, 1, 0),
                0, List.of(), YESTERDAY, YESTERDAY.plusMinutes(2)));

        mockMvc.perform(get("/api/admin/crawling/dashboard").with(user(adminDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalCollectedToday").value(0))
                .andExpect(jsonPath("$.result.companies[?(@.id == 'kakao')].collectedToday").value(0))
                .andExpect(jsonPath("$.result.companies[?(@.id == 'kakao')].status").value("healthy"))
                .andExpect(jsonPath("$.result.lastCrawledAt").exists());
    }

    @Test
    @DisplayName("일반 사용자는 수집 현황을 볼 수 없다")
    void rejectsNonAdmin() throws Exception {
        User member = saveUser("member@example.com", "일반유저", Role.USER);

        mockMvc.perform(get("/api/admin/crawling/dashboard").with(user(new UserDetailsImpl(member))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("로그인하지 않으면 수집 현황을 볼 수 없다")
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/admin/crawling/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("현황에 지금 수집이 돌고 있는지 함께 알려준다")
    void reportsRunningState() throws Exception {
        given(crawlLauncher.isRunning()).willReturn(true);

        mockMvc.perform(get("/api/admin/crawling/dashboard").with(user(adminDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.running").value(true));
    }

    @Test
    @DisplayName("관리자가 수집을 요청하면 뒤에서 시작하고 현황을 바로 돌려준다")
    void startsCrawlingInBackground() throws Exception {
        given(crawlLauncher.isRunning()).willReturn(true);

        mockMvc.perform(post("/api/admin/crawling/run").with(user(adminDetails())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.running").value(true))
                .andExpect(jsonPath("$.result.companies").isArray());

        verify(crawlLauncher).startInBackground();
    }

    @Test
    @DisplayName("이미 수집이 돌고 있으면 새로 시작하지 않고 409 를 준다")
    void rejectsStartWhileRunning() throws Exception {
        willThrow(new GeneralException(GeneralErrorCode.CRAWL_ALREADY_RUNNING))
                .given(crawlLauncher).startInBackground();

        mockMvc.perform(post("/api/admin/crawling/run").with(user(adminDetails())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_4091"));
    }

    @Test
    @DisplayName("일반 사용자는 수집을 시작할 수 없다")
    void rejectsNonAdminStart() throws Exception {
        User member = saveUser("member-run@example.com", "일반유저", Role.USER);

        mockMvc.perform(post("/api/admin/crawling/run").with(user(new UserDetailsImpl(member))))
                .andExpect(status().isForbidden());

        verify(crawlLauncher, never()).startInBackground();
    }

    private UserDetailsImpl adminDetails() {
        return new UserDetailsImpl(saveUser("admin-%s@example.com".formatted(createdUsers.size()), "관리자", Role.ADMIN));
    }

    private User saveUser(String email, String nickname, Role role) {
        User saved = userRepository.save(
                User.builder()
                        .email(email)
                        .password("encoded-password")
                        .socialType(SocialType.LOCAL)
                        .nickname(nickname)
                        .username(nickname)
                        .role(role)
                        .build()
        );
        createdUsers.add(saved);
        return saved;
    }
}
