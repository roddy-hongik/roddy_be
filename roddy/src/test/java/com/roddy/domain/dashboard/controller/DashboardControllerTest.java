package com.roddy.domain.dashboard.controller;

import com.roddy.domain.analysis.dto.CompetencyCategory;
import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.analysis.entity.AnalysisReportCategory;
import com.roddy.domain.analysis.entity.StackDetail;
import com.roddy.domain.analysis.entity.UserStack;
import com.roddy.domain.analysis.repository.AnalysisReportCategoryRepository;
import com.roddy.domain.analysis.repository.AnalysisReportRepository;
import com.roddy.domain.analysis.repository.StackDetailRepository;
import com.roddy.domain.analysis.repository.UserStackRepository;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.enums.ExperienceLevel;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.domain.jobposting.dto.JobPostingSnapshot;
import com.roddy.domain.jobposting.entity.JobPosting;
import com.roddy.domain.jobposting.repository.JobPostingRepository;
import com.roddy.domain.mypage.entity.DesiredCompany;
import com.roddy.domain.mypage.repository.DesiredCompanyRepository;
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
class DashboardControllerTest {

    private static final LocalDateTime CRAWLED_AT = LocalDateTime.of(2026, 3, 16, 4, 0);
    private static final String USER_NAME = "대시보드유저";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private AnalysisReportRepository analysisReportRepository;

    @Autowired
    private AnalysisReportCategoryRepository analysisReportCategoryRepository;

    @Autowired
    private UserStackRepository userStackRepository;

    @Autowired
    private StackDetailRepository stackDetailRepository;

    @Autowired
    private DesiredCompanyRepository desiredCompanyRepository;

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
    }

    @AfterEach
    void tearDown() {
        clear();
        userRepository.deleteAll(createdUsers);
        createdUsers.clear();
    }

    @Test
    @DisplayName("가장 최근에 끝난 리포트의 축별 점수와 기술, 매칭률이 높은 공고를 모아 준다")
    void summarizesLatestReportAndRecommendedJobs() throws Exception {
        User user = saveUser("dashboard@example.com", DesiredJob.BACKEND);
        desiredCompanyRepository.save(DesiredCompany.create(user, DesiredJob.BACKEND, "카카오"));
        AnalysisReport report = analysisReportRepository.save(completedReport(user));
        analysisReportCategoryRepository.save(AnalysisReportCategory.create(
                report, new CompetencyCategory("ARCHITECTURE", "비즈니스 로직의 아키텍처 설계", "유지보수성과 확장성"),
                80, "계층을 나눠 설계했다"));
        saveUserStack(user, report, "Spring Boot", 70, "ARCHITECTURE");
        saveUserStack(user, report, "Java", 90, "ARCHITECTURE");
        savePosting("P-1", Set.of("Java", "Spring Boot"));
        savePosting("P-2", Set.of("Java", "Kafka"));
        savePosting("P-3", Set.of("Kafka"));

        mockMvc.perform(get("/api/dashboard/summary").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.userName").value(USER_NAME))
                .andExpect(jsonPath("$.result.desiredJob").value("BACKEND"))
                .andExpect(jsonPath("$.result.desiredCompany").value("카카오"))
                .andExpect(jsonPath("$.result.reportId").value(report.getId()))
                .andExpect(jsonPath("$.result.categories.length()").value(1))
                .andExpect(jsonPath("$.result.categories[0].code").value("ARCHITECTURE"))
                .andExpect(jsonPath("$.result.categories[0].score").value(80))
                .andExpect(jsonPath("$.result.categories[0].interpretation").value("계층을 나눠 설계했다"))
                .andExpect(jsonPath("$.result.categories[0].stacks.length()").value(2))
                // 숙련도 점수가 높은 기술이 앞에 온다.
                .andExpect(jsonPath("$.result.techKeywords[0]").value("Java"))
                .andExpect(jsonPath("$.result.techKeywords[1]").value("Spring Boot"))
                // (90 + 70) / 2 = 80, (90 + 0) / 2 = 45. 요구 기술을 하나도 갖지 못한 공고는 추천하지 않는다.
                .andExpect(jsonPath("$.result.recommendedJobs.length()").value(2))
                .andExpect(jsonPath("$.result.recommendedJobs[0].matchRate").value(80))
                .andExpect(jsonPath("$.result.recommendedJobs[1].matchRate").value(45))
                .andExpect(jsonPath("$.result.bestMatchRate").value(80));
    }

    @Test
    @DisplayName("다시 분석하는 중이어도 가장 최근에 끝난 리포트로 그린다")
    void usesLatestCompletedReportWhileAnalyzing() throws Exception {
        User user = saveUser("dashboard-analyzing@example.com", DesiredJob.BACKEND);
        AnalysisReport completed = analysisReportRepository.save(completedReport(user));
        saveUserStack(user, completed, "Java", 90, null);
        // 분석 중인 리포트는 아직 비어 있다. 이걸로 그리면 다시 분석하는 동안 대시보드가 텅 빈다.
        analysisReportRepository.save(AnalysisReport.pending(user));

        mockMvc.perform(get("/api/dashboard/summary").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.reportId").value(completed.getId()))
                .andExpect(jsonPath("$.result.techKeywords[0]").value("Java"));
    }

    @Test
    @DisplayName("끝난 분석이 없으면 점수와 추천을 지어내지 않고 비워 둔다")
    void leavesScoresEmptyWithoutAnalysis() throws Exception {
        User user = saveUser("dashboard-empty@example.com", DesiredJob.FRONTEND);
        savePosting("P-1", Set.of("Java"));

        mockMvc.perform(get("/api/dashboard/summary").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.userName").value(USER_NAME))
                .andExpect(jsonPath("$.result.desiredJob").value("FRONTEND"))
                .andExpect(jsonPath("$.result.reportId").doesNotExist())
                .andExpect(jsonPath("$.result.categories.length()").value(0))
                .andExpect(jsonPath("$.result.techKeywords.length()").value(0))
                .andExpect(jsonPath("$.result.bestMatchRate").doesNotExist())
                .andExpect(jsonPath("$.result.recommendedJobs.length()").value(0));
    }

    @Test
    @DisplayName("로그인하지 않으면 대시보드를 볼 수 없다")
    void rejectsAnonymous() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    private AnalysisReport completedReport(User user) {
        AnalysisReport report = AnalysisReport.pending(user);
        report.complete("백엔드 주니어", 70, "요약", "깃허브 분석", "포트폴리오 분석", CRAWLED_AT);
        return report;
    }

    private void saveUserStack(User user, AnalysisReport report, String stackName, int score, String categoryCode) {
        StackDetail detail = stackDetailRepository.save(StackDetail.ofName(stackName));
        userStackRepository.save(UserStack.create(
                user, detail, report, null, score, "설명", categoryCode, true, false));
    }

    private void savePosting(String externalId, Set<String> techStacks) {
        JobPosting posting = jobPostingRepository.save(JobPosting.create(
                JobPostingSnapshot.builder()
                        .companyCode("kakao")
                        .company("카카오")
                        .externalId(externalId)
                        .title("백엔드 개발자 " + externalId)
                        .applyUrl("https://careers.kakao.com/jobs/" + externalId)
                        .build(),
                CRAWLED_AT));

        posting.updateTechStacks(techStacks);
        jobPostingRepository.save(posting);
    }

    private void clear() {
        analysisReportCategoryRepository.deleteAll();
        userStackRepository.deleteAll();
        analysisReportRepository.deleteAll();
        stackDetailRepository.deleteAll();
        jobPostingRepository.deleteAll();
        desiredCompanyRepository.deleteAll();
    }

    private User saveUser(String email, DesiredJob desiredJob) {
        User user = User.builder()
                .email(email)
                .password("encoded-password")
                .socialType(SocialType.LOCAL)
                .nickname(USER_NAME)
                .username(USER_NAME)
                .role(Role.USER)
                .build();
        user.completeProfile(USER_NAME, 27, ExperienceLevel.JUNIOR, desiredJob,
                "portfolio/1/portfolio.pdf", "portfolio.pdf", CRAWLED_AT);
        User saved = userRepository.save(user);
        createdUsers.add(saved);
        return saved;
    }
}
