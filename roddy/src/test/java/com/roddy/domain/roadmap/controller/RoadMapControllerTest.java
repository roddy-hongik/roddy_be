package com.roddy.domain.roadmap.controller;

import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.analysis.entity.StackDetail;
import com.roddy.domain.analysis.entity.UserStack;
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
import com.roddy.domain.roadmap.repository.RoadMapRepository;
import com.roddy.global.client.roadmap.RoadMapAiClient;
import com.roddy.global.client.roadmap.RoadMapAiResponse;
import com.roddy.global.config.s3.S3Uploader;
import com.roddy.global.security.UserDetailsImpl;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class RoadMapControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 12, 0);

    @Autowired private WebApplicationContext context;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private UserStackRepository userStackRepository;
    @Autowired private StackDetailRepository stackDetailRepository;
    @Autowired private AnalysisReportRepository analysisReportRepository;
    @Autowired private DesiredCompanyRepository desiredCompanyRepository;
    @Autowired private UserRepository userRepository;

    @MockitoSpyBean private RoadMapRepository roadMapRepository;
    @MockitoBean private RoadMapAiClient roadMapAiClient;
    @MockitoBean private S3Uploader s3Uploader;
    @MockitoBean private SocialAuthService socialAuthService;

    private MockMvc mockMvc;
    private final List<User> users = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        clear();
    }

    @AfterEach
    void tearDown() {
        clear();
        userRepository.deleteAll(users);
        users.clear();
    }

    @Test
    void 최신_분석의_기술과_채용공고의_부족_기술을_요약한다() throws Exception {
        User user = analyzedUser("roadmap-summary@example.com");
        savePosting("P-1", Set.of("Java", "Redis"));
        savePosting("P-2", Set.of("Redis", "Kafka"));

        mockMvc.perform(get("/api/roadmap/summary").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.currentSkills[0]").value("Java"))
                .andExpect(jsonPath("$.result.currentSkills[1]").value("Spring Boot"))
                .andExpect(jsonPath("$.result.gapSkills[0]").value("Redis"))
                .andExpect(jsonPath("$.result.gapSkills[1]").value("Kafka"))
                .andExpect(jsonPath("$.result.targetJob").value("백엔드 개발자"))
                .andExpect(jsonPath("$.result.targetCompany").value("토스"));
    }

    @Test
    void AI가_만든_세_단계_로드맵을_반환한다() throws Exception {
        User user = analyzedUser("roadmap-generate@example.com");
        savePosting("P-1", Set.of("Redis"));
        when(roadMapAiClient.generate(any())).thenReturn(generated("기초", "심화", "실전 프로젝트"));

        mockMvc.perform(post("/api/roadmap/generate").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.title").value("백엔드 성장 로드맵"))
                .andExpect(jsonPath("$.result.currentSkills[0]").value("Java"))
                .andExpect(jsonPath("$.result.gapSkills[0]").value("Redis"))
                .andExpect(jsonPath("$.result.targetJob").value("백엔드 개발자"))
                .andExpect(jsonPath("$.result.targetCompany").value("토스"))
                .andExpect(jsonPath("$.result.steps.length()").value(3))
                .andExpect(jsonPath("$.result.steps[0].stage").value("기초"))
                .andExpect(jsonPath("$.result.steps[2].stage").value("실전 프로젝트"));
    }

    @Test
    void AI가_단계_순서를_어기면_사용자_요청_오류가_아니라_서비스_오류로_답한다() throws Exception {
        User user = analyzedUser("roadmap-invalid-stage@example.com");
        savePosting("P-1", Set.of("Redis"));
        when(roadMapAiClient.generate(any())).thenReturn(generated("심화", "기초", "실전 프로젝트"));

        mockMvc.perform(post("/api/roadmap/generate").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVER_5031"));
    }

    @Test
    void 같은_로드맵은_한_번만_저장하고_최신순으로_조회한다() throws Exception {
        User user = analyzedUser("roadmap-save@example.com");
        String body = saveBody("백엔드 성장 로드맵", "Redis");

        saveRoadMap(user, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.saved").value(true))
                .andExpect(jsonPath("$.result.roadmap.roadmapTitle").value("백엔드 성장 로드맵"))
                .andExpect(jsonPath("$.result.roadmap.roadmapSteps.length()").value(3));

        saveRoadMap(user, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.saved").value(false))
                .andExpect(jsonPath("$.result.reason").value("duplicate"));

        mockMvc.perform(get("/api/roadmap/saved").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.roadmaps.length()").value(1))
                .andExpect(jsonPath("$.result.roadmaps[0].gapSkills[0]").value("Redis"))
                .andExpect(jsonPath("$.result.totalElements").value(1));
    }

    @Test
    void 중복_확인과_저장_사이에_같은_로드맵이_먼저_저장되면_중복으로_답한다() throws Exception {
        User user = analyzedUser("roadmap-race@example.com");
        String body = saveBody("백엔드 성장 로드맵", "Redis");
        saveRoadMap(user, body).andExpect(jsonPath("$.result.saved").value(true));

        // 다른 요청이 먼저 저장한 것을 첫 중복 확인에서는 못 본 상황. 충돌 뒤 다시 조회할 때는 실제로 읽는다.
        Answer<?> realMethod = Mockito.mockingDetails(roadMapRepository).getMockCreationSettings().getDefaultAnswer();
        doReturn(Optional.empty()).doAnswer(realMethod)
                .when(roadMapRepository).findFirstByUserIdAndFingerprint(any(), any());
        clearInvocations(roadMapRepository);

        saveRoadMap(user, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.saved").value(false))
                .andExpect(jsonPath("$.result.reason").value("duplicate"))
                .andExpect(jsonPath("$.result.roadmap.roadmapTitle").value("백엔드 성장 로드맵"));

        verify(roadMapRepository, times(2)).findFirstByUserIdAndFingerprint(any(), any());
        assertThat(roadMapRepository.count()).isEqualTo(1);
    }

    @Test
    void 저장한_로드맵을_페이지로_나눠_최신순으로_조회한다() throws Exception {
        User user = analyzedUser("roadmap-page@example.com");
        saveRoadMap(user, saveBody("먼저 저장한 로드맵", "Redis"));
        saveRoadMap(user, saveBody("나중에 저장한 로드맵", "Kafka"));

        mockMvc.perform(get("/api/roadmap/saved").param("page", "0").param("size", "1")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.roadmaps.length()").value(1))
                .andExpect(jsonPath("$.result.roadmaps[0].roadmapTitle").value("나중에 저장한 로드맵"))
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.totalPages").value(2));

        mockMvc.perform(get("/api/roadmap/saved").param("page", "1").param("size", "1")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(jsonPath("$.result.roadmaps[0].roadmapTitle").value("먼저 저장한 로드맵"));
    }

    @Test
    void 저장한_로드맵이_늘어도_목록_조회_쿼리_수는_그대로다() throws Exception {
        User user = analyzedUser("roadmap-query-count@example.com");
        saveRoadMap(user, saveBody("첫 로드맵", "Redis"));
        long single = countStatements(() -> mockMvc.perform(
                get("/api/roadmap/saved").with(user(new UserDetailsImpl(user)))));

        saveRoadMap(user, saveBody("둘째 로드맵", "Kafka"));
        saveRoadMap(user, saveBody("셋째 로드맵", "Docker"));
        long triple = countStatements(() -> mockMvc.perform(
                get("/api/roadmap/saved").with(user(new UserDetailsImpl(user))))
                .andExpect(jsonPath("$.result.roadmaps.length()").value(3)));

        assertThat(triple).isEqualTo(single);
    }

    @Test
    void 공백을_걷어낸_기술_이름이_100자를_넘으면_저장하지_않는다() throws Exception {
        User user = analyzedUser("roadmap-skill-length@example.com");

        saveRoadMap(user, saveBody("걷어내면 100자", "  " + "a".repeat(100) + "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.roadmap.gapSkills[0]").value("a".repeat(100)));

        saveRoadMap(user, saveBody("걷어내도 101자", "a".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQ_4002"))
                .andExpect(jsonPath("$.error[0]").value(containsString("gapSkills")));
    }

    @Test
    void 완료된_분석이_없으면_요약을_만들지_않는다() throws Exception {
        User user = saveUser("roadmap-no-analysis@example.com");

        mockMvc.perform(get("/api/roadmap/summary").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS_4041"));
    }

    @Test
    void 부족_기술이_없으면_AI를_호출하지_않는다() throws Exception {
        User user = analyzedUser("roadmap-no-gap@example.com");

        mockMvc.perform(post("/api/roadmap/generate").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROADMAP_4001"));
    }

    @Test
    void 비로그인_사용자는_로드맵을_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/roadmap/summary")).andExpect(status().isUnauthorized());
    }

    private User analyzedUser(String email) {
        User user = saveUser(email);
        desiredCompanyRepository.save(DesiredCompany.create(user, DesiredJob.BACKEND, "토스"));
        AnalysisReport report = AnalysisReport.pending(user);
        report.complete("백엔드 분석", 80, "요약", "깃허브", "포트폴리오", NOW);
        report = analysisReportRepository.save(report);
        saveStack(user, report, "Spring Boot", 70);
        saveStack(user, report, "Java", 90);
        return user;
    }

    private User saveUser(String email) {
        User user = User.builder()
                .email(email)
                .password("encoded-password")
                .socialType(SocialType.LOCAL)
                .nickname("로드맵유저")
                .username("로드맵유저")
                .role(Role.USER)
                .build();
        user.completeProfile("로드맵유저", 27, ExperienceLevel.JUNIOR, DesiredJob.BACKEND,
                "portfolio/1/file.pdf", "file.pdf", NOW);
        User saved = userRepository.save(user);
        users.add(saved);
        return saved;
    }

    private void saveStack(User user, AnalysisReport report, String name, int score) {
        StackDetail detail = stackDetailRepository.save(StackDetail.ofName(name));
        userStackRepository.save(UserStack.create(
                user, detail, report, null, score, "설명", null, true, false));
    }

    private void savePosting(String externalId, Set<String> stacks) {
        JobPosting posting = jobPostingRepository.save(JobPosting.create(
                JobPostingSnapshot.builder()
                        .companyCode("toss")
                        .externalId(externalId)
                        .company("토스")
                        .title("백엔드 개발자")
                        .desiredJob(DesiredJob.BACKEND)
                        .applyUrl("https://example.com/" + externalId)
                        .build(),
                NOW));
        posting.updateTechStacks(stacks);
        jobPostingRepository.save(posting);
    }

    private ResultActions saveRoadMap(User user, String body) throws Exception {
        return mockMvc.perform(post("/api/roadmap/saved")
                .with(user(new UserDetailsImpl(user)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String saveBody(String title, String gapSkill) {
        return """
                {
                  "title": "%s",
                  "steps": [
                    {"stage":"기초","goal":"기초 목표","topics":["Redis"],"outputs":["예제"]},
                    {"stage":"심화","goal":"심화 목표","topics":["분산 락"],"outputs":["부하 테스트"]},
                    {"stage":"실전 프로젝트","goal":"실전 목표","topics":["캐시"],"outputs":["프로젝트"]}
                  ],
                  "currentSkills": ["Java", "Spring Boot"],
                  "gapSkills": ["%s"],
                  "targetJob": "백엔드 개발자",
                  "targetCompany": "토스"
                }
                """.formatted(title, gapSkill);
    }

    private long countStatements(ThrowingRunnable request) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            request.run();
            return statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private RoadMapAiResponse generated(String... stages) {
        return new RoadMapAiResponse("백엔드 성장 로드맵", List.of(
                new RoadMapAiResponse.Step(stages[0], "기초 목표", List.of("Redis"), List.of("예제")),
                new RoadMapAiResponse.Step(stages[1], "심화 목표", List.of("분산 락"), List.of("부하 테스트")),
                new RoadMapAiResponse.Step(stages[2], "실전 목표", List.of("캐시"), List.of("프로젝트"))
        ));
    }

    private void clear() {
        roadMapRepository.deleteAll();
        userStackRepository.deleteAll();
        analysisReportRepository.deleteAll();
        stackDetailRepository.deleteAll();
        jobPostingRepository.deleteAll();
        desiredCompanyRepository.deleteAll();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
