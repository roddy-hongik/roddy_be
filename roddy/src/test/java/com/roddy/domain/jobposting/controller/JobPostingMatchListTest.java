package com.roddy.domain.jobposting.controller;

import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.analysis.entity.StackDetail;
import com.roddy.domain.analysis.entity.UserStack;
import com.roddy.domain.analysis.repository.AnalysisReportRepository;
import com.roddy.domain.analysis.repository.StackDetailRepository;
import com.roddy.domain.analysis.repository.UserStackRepository;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.domain.enums.Stack;
import com.roddy.domain.jobposting.dto.JobPostingSnapshot;
import com.roddy.domain.jobposting.entity.JobPosting;
import com.roddy.domain.jobposting.repository.JobPostingRepository;
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
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class JobPostingMatchListTest {

    private static final LocalDateTime CRAWLED_AT = LocalDateTime.of(2026, 3, 16, 4, 0);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private UserStackRepository userStackRepository;

    @Autowired
    private StackDetailRepository stackDetailRepository;

    @Autowired
    private AnalysisReportRepository analysisReportRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private S3Uploader s3Uploader;

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
        // 최신순은 최신공고 → 중간공고 → 오래된공고. 매칭률 순은 그 반대가 되도록 짰다.
        savePosting("P-1", "최신공고", LocalDateTime.of(2026, 3, 14, 0, 0), Set.of("Kafka", "Kubernetes"));
        savePosting("P-2", "중간공고", LocalDateTime.of(2026, 3, 12, 0, 0), Set.of("Java", "Spring Boot"));
        savePosting("P-3", "오래된공고", LocalDateTime.of(2026, 3, 10, 0, 0), Set.of("Java", "Kafka"));
    }

    @AfterEach
    void tearDown() {
        clear();
        userRepository.deleteAll(createdUsers);
        createdUsers.clear();
    }

    @Test
    @DisplayName("목록에 공고마다 내 매칭률을 함께 준다")
    void includesMatchRateInList() throws Exception {
        User user = analyzedUser("list@example.com");

        mockMvc.perform(get("/api/jobs").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                // 최신순이므로 최신공고(Kafka, Kubernetes)가 먼저. 둘 다 없으니 0
                .andExpect(jsonPath("$.result.jobs[0].title").value("최신공고"))
                .andExpect(jsonPath("$.result.jobs[0].matchRate").value(0))
                // (90 + 80) / 2
                .andExpect(jsonPath("$.result.jobs[1].matchRate").value(85))
                // (90 + 0) / 2
                .andExpect(jsonPath("$.result.jobs[2].matchRate").value(45));
    }

    @Test
    @DisplayName("분석 결과가 없으면 매칭률을 비워 둔다")
    void leavesMatchRateEmptyWithoutAnalysis() throws Exception {
        User user = saveUser("no-analysis@example.com");

        mockMvc.perform(get("/api/jobs").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.jobs[0].matchRate").doesNotExist());
    }

    @Test
    @DisplayName("로그인하지 않으면 매칭률 없이 목록만 준다")
    void leavesMatchRateEmptyForAnonymous() throws Exception {
        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.jobs[0].matchRate").doesNotExist());
    }

    @Test
    @DisplayName("매칭률 순으로 정렬하면 페이지를 넘어 전체에서 높은 공고가 먼저 온다")
    void sortsByMatchRateAcrossPages() throws Exception {
        User user = analyzedUser("sort@example.com");

        // 한 페이지에 한 건씩 봐도 전체를 견줘 정렬해야 한다. 최신순이었다면 최신공고가 왔을 자리다.
        mockMvc.perform(get("/api/jobs")
                        .param("sort", "match").param("page", "0").param("size", "1")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(3))
                .andExpect(jsonPath("$.result.jobs[0].title").value("중간공고"))
                .andExpect(jsonPath("$.result.jobs[0].matchRate").value(85));

        mockMvc.perform(get("/api/jobs")
                        .param("sort", "match").param("page", "1").param("size", "1")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.jobs[0].title").value("오래된공고"));

        mockMvc.perform(get("/api/jobs")
                        .param("sort", "match").param("page", "2").param("size", "1")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.jobs[0].title").value("최신공고"));
    }

    @Test
    @DisplayName("검색 조건을 걸어도 그 안에서 매칭률 순으로 정렬한다")
    void sortsWithinFilteredResult() throws Exception {
        User user = analyzedUser("filtered@example.com");

        mockMvc.perform(get("/api/jobs")
                        .param("sort", "match").param("keyword", "공고")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(3))
                .andExpect(jsonPath("$.result.jobs[0].title").value("중간공고"));
    }

    @Test
    @DisplayName("분석 결과가 없으면 매칭률 순을 요청해도 최신순을 유지한다")
    void keepsLatestOrderWithoutAnalysis() throws Exception {
        User user = saveUser("no-analysis-sort@example.com");

        // 전부 매칭률이 비어 있어 정렬할 것이 없다. 순서를 흔들면 사용자만 혼란스럽다.
        mockMvc.perform(get("/api/jobs")
                        .param("sort", "match")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.jobs[0].title").value("최신공고"));
    }

    private void clear() {
        userStackRepository.deleteAll();
        analysisReportRepository.deleteAll();
        stackDetailRepository.deleteAll();
        jobPostingRepository.deleteAll();
    }

    private void savePosting(String externalId, String title, LocalDateTime postedAt, Set<String> techStacks) {
        JobPosting posting = jobPostingRepository.save(JobPosting.create(
                JobPostingSnapshot.builder()
                        .companyCode("kakao")
                        .company("카카오")
                        .externalId(externalId)
                        .title(title)
                        .postedAt(postedAt)
                        .applyUrl("https://careers.kakao.com/jobs/" + externalId)
                        .build(),
                CRAWLED_AT));

        posting.updateTechStacks(techStacks);
        jobPostingRepository.save(posting);
    }

    private User analyzedUser(String email) {
        User user = saveUser(email);
        AnalysisReport report = analysisReportRepository.save(completedReport(user));

        saveUserStack(user, report, "Java", 90);
        saveUserStack(user, report, "Spring Boot", 80);
        return user;
    }

    /** 분석이 끝난 리포트. 분석 도메인이 채우는 것과 같은 모양이다. */
    private AnalysisReport completedReport(User user) {
        AnalysisReport report = AnalysisReport.pending(user);
        report.complete("분석 리포트", 70, "요약", "깃허브 분석", "포트폴리오 분석", CRAWLED_AT);
        return report;
    }

    private void saveUserStack(User user, AnalysisReport report, String stackName, int score) {
        StackDetail detail = stackDetailRepository.save(
                StackDetail.create(Stack.ARCHITECTURE, stackName, stackName + " 숙련도"));
        userStackRepository.save(UserStack.create(user, detail, report, score, "설명"));
    }

    private User saveUser(String email) {
        User saved = userRepository.save(
                User.builder()
                        .email(email)
                        .password("encoded-password")
                        .socialType(SocialType.LOCAL)
                        .nickname("매칭목록유저")
                        .username("매칭목록유저")
                        .role(Role.USER)
                        .build()
        );
        createdUsers.add(saved);
        return saved;
    }
}
