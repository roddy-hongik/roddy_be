package com.roddy.domain.interview.controller;

import com.roddy.domain.analysis.service.CompetencyGapReader;
import com.roddy.domain.analysis.service.CompetencyGapReader.CompetencyGap;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.enums.ExperienceLevel;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.global.client.interview.InterviewAiClient;
import com.roddy.global.client.interview.InterviewAiResponse;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class InterviewControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;

    @MockitoBean private CompetencyGapReader competencyGapReader;
    @MockitoBean private InterviewAiClient interviewAiClient;
    @MockitoBean private S3Uploader s3Uploader;
    @MockitoBean private SocialAuthService socialAuthService;

    private MockMvc mockMvc;
    private User testUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        testUser = userRepository.save(User.builder()
                .email("interview@example.com")
                .password("encoded-password")
                .socialType(SocialType.LOCAL)
                .nickname("면접유저")
                .username("면접유저")
                .role(Role.USER)
                .build());
        testUser.completeProfile("면접유저", 27, ExperienceLevel.JUNIOR, DesiredJob.BACKEND,
                "portfolio/1/file.pdf", "file.pdf", LocalDateTime.of(2026, 9, 13, 12, 0));
        userRepository.save(testUser);
    }

    @AfterEach
    void tearDown() {
        userRepository.delete(testUser);
    }

    @Test
    void 역량_격차로_질문_세_개를_생성한다() throws Exception {
        when(competencyGapReader.read(testUser.getId())).thenReturn(gap(List.of("Redis")));
        when(interviewAiClient.generate(any())).thenReturn(generatedQuestions());

        mockMvc.perform(post("/api/mock-interview/questions")
                        .with(user(new UserDetailsImpl(testUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.questions.length()").value(3))
                .andExpect(jsonPath("$.result.questions[0].id").value("q1"))
                .andExpect(jsonPath("$.result.questions[0].question").value("Redis 캐시 전략을 설명해 주세요."))
                .andExpect(jsonPath("$.result.questions[0].keyPoints[0]").value("TTL"));
    }

    @Test
    void 부족_기술이_없으면_AI를_호출하지_않는다() throws Exception {
        when(competencyGapReader.read(testUser.getId())).thenReturn(gap(List.of()));

        mockMvc.perform(post("/api/mock-interview/questions")
                        .with(user(new UserDetailsImpl(testUser))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INTERVIEW_4001"));
        verifyNoInteractions(interviewAiClient);
    }

    @Test
    void AI_응답이_중복되면_서비스_오류를_반환한다() throws Exception {
        when(competencyGapReader.read(testUser.getId())).thenReturn(gap(List.of("Redis")));
        InterviewAiResponse.Question duplicated = question("q1", "같은 질문");
        when(interviewAiClient.generate(any())).thenReturn(
                new InterviewAiResponse(List.of(duplicated, question("q2", "같은 질문"), question("q3", "다른 질문"))));

        mockMvc.perform(post("/api/mock-interview/questions")
                        .with(user(new UserDetailsImpl(testUser))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVER_5031"));
    }

    @Test
    void 비로그인_사용자는_질문을_생성할_수_없다() throws Exception {
        mockMvc.perform(post("/api/mock-interview/questions"))
                .andExpect(status().isUnauthorized());
    }

    private CompetencyGap gap(List<String> gapSkills) {
        return new CompetencyGap(
                testUser, DesiredJob.BACKEND, "토스",
                List.of("Java", "Spring Boot"), gapSkills);
    }

    private InterviewAiResponse generatedQuestions() {
        return new InterviewAiResponse(List.of(
                question("q1", "Redis 캐시 전략을 설명해 주세요."),
                question("q2", "분산 락은 언제 필요합니까?"),
                question("q3", "캐시 장애에 어떻게 대응합니까?")
        ));
    }

    private InterviewAiResponse.Question question(String id, String question) {
        return new InterviewAiResponse.Question(id, question, "평가 의도", List.of("TTL", "모니터링"));
    }
}
