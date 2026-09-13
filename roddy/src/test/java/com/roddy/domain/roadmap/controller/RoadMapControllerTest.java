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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
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
    @Autowired private RoadMapRepository roadMapRepository;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private UserStackRepository userStackRepository;
    @Autowired private StackDetailRepository stackDetailRepository;
    @Autowired private AnalysisReportRepository analysisReportRepository;
    @Autowired private DesiredCompanyRepository desiredCompanyRepository;
    @Autowired private UserRepository userRepository;

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
        when(roadMapAiClient.generate(any())).thenReturn(generated());

        mockMvc.perform(post("/api/roadmap/generate").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.title").value("백엔드 성장 로드맵"))
                .andExpect(jsonPath("$.result.steps.length()").value(3))
                .andExpect(jsonPath("$.result.steps[0].stage").value("기초"))
                .andExpect(jsonPath("$.result.steps[2].stage").value("실전 프로젝트"));
    }

    @Test
    void 같은_로드맵은_한_번만_저장하고_최신순으로_조회한다() throws Exception {
        User user = analyzedUser("roadmap-save@example.com");
        savePosting("P-1", Set.of("Redis"));
        String body = """
                {
                  "title": "백엔드 성장 로드맵",
                  "steps": [
                    {"stage":"기초","goal":"기초 목표","topics":["Redis"],"outputs":["예제"]},
                    {"stage":"심화","goal":"심화 목표","topics":["분산 락"],"outputs":["부하 테스트"]},
                    {"stage":"실전 프로젝트","goal":"실전 목표","topics":["캐시"],"outputs":["프로젝트"]}
                  ]
                }
                """;

        mockMvc.perform(post("/api/roadmap/saved")
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.saved").value(true))
                .andExpect(jsonPath("$.result.roadmap.roadmapTitle").value("백엔드 성장 로드맵"))
                .andExpect(jsonPath("$.result.roadmap.roadmapSteps.length()").value(3));

        mockMvc.perform(post("/api/roadmap/saved")
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.saved").value(false))
                .andExpect(jsonPath("$.result.reason").value("duplicate"));

        mockMvc.perform(get("/api/roadmap/saved").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].gapSkills[0]").value("Redis"));
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

    private RoadMapAiResponse generated() {
        return new RoadMapAiResponse("백엔드 성장 로드맵", List.of(
                new RoadMapAiResponse.Step("기초", "기초 목표", List.of("Redis"), List.of("예제")),
                new RoadMapAiResponse.Step("심화", "심화 목표", List.of("분산 락"), List.of("부하 테스트")),
                new RoadMapAiResponse.Step("실전 프로젝트", "실전 목표", List.of("캐시"), List.of("프로젝트"))
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
}
