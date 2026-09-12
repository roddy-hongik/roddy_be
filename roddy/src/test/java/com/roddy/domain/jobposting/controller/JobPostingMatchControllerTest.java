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
class JobPostingMatchControllerTest {

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

        clearAnalysisAndPostings();
    }

    @AfterEach
    void tearDown() {
        clearAnalysisAndPostings();
        userRepository.deleteAll(createdUsers);
        createdUsers.clear();
    }

    @Test
    @DisplayName("공고가 요구하는 기술마다 내 숙련도를 견줘 매칭률을 낸다")
    void calculatesMatchRate() throws Exception {
        User user = saveUser("match@example.com");
        saveUserStack(user, "Java", 80);
        saveUserStack(user, "Spring Boot", 70);
        JobPosting posting = savePosting("P-1", Set.of("Java", "Spring Boot", "Kafka", "Kubernetes"));

        // (80 + 70 + 0 + 0) / 4 = 37.5 → 38
        mockMvc.perform(get("/api/jobs/{jobPostingId}/match", posting.getId())
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.matchRate").value(38))
                .andExpect(jsonPath("$.result.requiredCount").value(4))
                .andExpect(jsonPath("$.result.matchedCount").value(2))
                .andExpect(jsonPath("$.result.userStackCount").value(2))
                .andExpect(jsonPath("$.result.stacks[0].name").value("Java"))
                .andExpect(jsonPath("$.result.stacks[0].userScore").value(80))
                .andExpect(jsonPath("$.result.stacks[0].held").value(true));
    }

    @Test
    @DisplayName("가지고 있지 않은 기술을 부족한 기술로 알려준다")
    void reportsMissingStacks() throws Exception {
        User user = saveUser("missing@example.com");
        saveUserStack(user, "Java", 90);
        JobPosting posting = savePosting("P-1", Set.of("Java", "Kafka"));

        mockMvc.perform(get("/api/jobs/{jobPostingId}/match", posting.getId())
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.missingStacks.length()").value(1))
                .andExpect(jsonPath("$.result.missingStacks[0]").value("Kafka"));
    }

    @Test
    @DisplayName("한글로 적힌 내 기술도 공고의 기술과 이어진다")
    void matchesKoreanStackName() throws Exception {
        User user = saveUser("korean@example.com");
        saveUserStack(user, "자바", 60);
        JobPosting posting = savePosting("P-1", Set.of("Java"));

        mockMvc.perform(get("/api/jobs/{jobPostingId}/match", posting.getId())
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.matchRate").value(60))
                .andExpect(jsonPath("$.result.matchedCount").value(1));
    }

    @Test
    @DisplayName("아직 분석 결과가 없으면 매칭률을 숫자로 내지 않는다")
    void doesNotScoreWithoutUserStacks() throws Exception {
        User user = saveUser("no-analysis@example.com");
        JobPosting posting = savePosting("P-1", Set.of("Java", "Kafka"));

        // 0% 로 주면 "적합하지 않다"로 읽힌다. 아직 판단할 근거가 없다는 뜻이어야 한다.
        mockMvc.perform(get("/api/jobs/{jobPostingId}/match", posting.getId())
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.matchRate").doesNotExist())
                .andExpect(jsonPath("$.result.userStackCount").value(0))
                .andExpect(jsonPath("$.result.requiredCount").value(2))
                .andExpect(jsonPath("$.result.missingStacks.length()").value(2));
    }

    @Test
    @DisplayName("공고에서 기술을 찾지 못했으면 매칭률을 숫자로 내지 않는다")
    void doesNotScoreWithoutJobStacks() throws Exception {
        User user = saveUser("no-job-stack@example.com");
        saveUserStack(user, "Java", 80);
        JobPosting posting = savePosting("P-1", Set.of());

        mockMvc.perform(get("/api/jobs/{jobPostingId}/match", posting.getId())
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.matchRate").doesNotExist())
                .andExpect(jsonPath("$.result.requiredCount").value(0));
    }

    @Test
    @DisplayName("없는 공고의 매칭률은 조회할 수 없다")
    void rejectsMissingPosting() throws Exception {
        User user = saveUser("not-found@example.com");

        mockMvc.perform(get("/api/jobs/{jobPostingId}/match", 999_999L)
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("JOB_4041"));
    }

    @Test
    @DisplayName("로그인하지 않으면 매칭률을 볼 수 없다")
    void rejectsAnonymous() throws Exception {
        JobPosting posting = savePosting("P-1", Set.of("Java"));

        // /api/jobs/* 는 한 칸짜리 경로만 열려 있어 /api/jobs/{id}/match 는 인증이 유지된다.
        mockMvc.perform(get("/api/jobs/{jobPostingId}/match", posting.getId()))
                .andExpect(status().isUnauthorized());
    }

    private void clearAnalysisAndPostings() {
        userStackRepository.deleteAll();
        analysisReportRepository.deleteAll();
        stackDetailRepository.deleteAll();
        jobPostingRepository.deleteAll();
    }

    private JobPosting savePosting(String externalId, Set<String> techStacks) {
        JobPosting posting = jobPostingRepository.save(JobPosting.create(
                JobPostingSnapshot.builder()
                        .companyCode("kakao")
                        .company("카카오")
                        .externalId(externalId)
                        .title("백엔드 개발자")
                        .applyUrl("https://careers.kakao.com/jobs/" + externalId)
                        .build(),
                CRAWLED_AT));

        posting.updateTechStacks(techStacks);
        return jobPostingRepository.save(posting);
    }

    private void saveUserStack(User user, String stackName, int score) {
        AnalysisReport report = analysisReportRepository.findByUserId(user.getId())
                .orElseGet(() -> analysisReportRepository.save(AnalysisReport.create(
                        user, "분석 리포트", 70, "요약", "깃허브 분석", "포트폴리오 분석")));
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
                        .nickname("매칭유저")
                        .username("매칭유저")
                        .role(Role.USER)
                        .build()
        );
        createdUsers.add(saved);
        return saved;
    }
}
