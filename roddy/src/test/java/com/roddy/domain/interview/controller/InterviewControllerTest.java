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
import com.roddy.global.client.interview.InterviewAiFeedbackResponse;
import com.roddy.global.client.interview.InterviewAiResponse;
import com.roddy.global.config.s3.S3Uploader;
import com.roddy.global.security.UserDetailsImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InterviewControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;

    @MockitoBean private CompetencyGapReader competencyGapReader;
    @MockitoBean private InterviewAiClient interviewAiClient;
    @MockitoBean private S3Uploader s3Uploader;
    @MockitoBean private SocialAuthService socialAuthService;

    private MockMvc mockMvc;
    private User testUser;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Test
    void 답변을_제출하면_AI_피드백을_받아_회차로_저장한다() throws Exception {
        when(interviewAiClient.generateFeedback(any())).thenReturn(feedbacks());

        String response = mockMvc.perform(post("/api/mock-interview/sessions")
                        .with(user(new UserDetailsImpl(testUser)))
                        .contentType(MediaType.APPLICATION_JSON).content(submitBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.answers.length()").value(3))
                .andExpect(jsonPath("$.result.answers[0].score").value(80))
                .andExpect(jsonPath("$.result.answers[0].feedback").value("TTL 언급은 좋았습니다."))
                .andExpect(jsonPath("$.result.answers[0].keyPoints[0]").value("TTL"))
                .andReturn().getResponse().getContentAsString();
        long sessionId = objectMapper.readTree(response).path("result").path("id").asLong();

        mockMvc.perform(get("/api/mock-interview/sessions/" + sessionId)
                        .with(user(new UserDetailsImpl(testUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.answers[1].id").value("q2"));
        mockMvc.perform(get("/api/mock-interview/sessions")
                        .with(user(new UserDetailsImpl(testUser))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.sessions[0].questionCount").value(3));
    }

    @Test
    void AI_피드백의_id가_요청과_다르면_서비스_오류를_반환한다() throws Exception {
        when(interviewAiClient.generateFeedback(any())).thenReturn(new InterviewAiFeedbackResponse(List.of(
                new InterviewAiFeedbackResponse.Feedback("다른아이디", 80, "피드백"),
                new InterviewAiFeedbackResponse.Feedback("q2", 70, "피드백"),
                new InterviewAiFeedbackResponse.Feedback("q3", 90, "피드백"))));

        mockMvc.perform(post("/api/mock-interview/sessions")
                        .with(user(new UserDetailsImpl(testUser)))
                        .contentType(MediaType.APPLICATION_JSON).content(submitBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVER_5031"));
    }

    @Test
    void 답변이_세_개가_아니면_검증에_실패한다() throws Exception {
        mockMvc.perform(post("/api/mock-interview/sessions")
                        .with(user(new UserDetailsImpl(testUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":[" + answerJson("q1", "질문", "의도", "포인트", "답변") + "]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(interviewAiClient);
    }

    @Test
    void 다른_사용자의_회차는_조회할_수_없다() throws Exception {
        when(interviewAiClient.generateFeedback(any())).thenReturn(feedbacks());
        String response = mockMvc.perform(post("/api/mock-interview/sessions")
                        .with(user(new UserDetailsImpl(testUser)))
                        .contentType(MediaType.APPLICATION_JSON).content(submitBody()))
                .andReturn().getResponse().getContentAsString();
        long sessionId = objectMapper.readTree(response).path("result").path("id").asLong();

        User other = userRepository.save(User.builder()
                .email("other-interview@example.com").password("encoded-password")
                .socialType(SocialType.LOCAL).nickname("다른유저").username("다른유저").role(Role.USER).build());

        mockMvc.perform(get("/api/mock-interview/sessions/" + sessionId)
                        .with(user(new UserDetailsImpl(other))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INTERVIEW_4041"));
    }

    @Test
    void 비로그인_사용자는_답변을_제출할_수_없다() throws Exception {
        mockMvc.perform(post("/api/mock-interview/sessions")
                        .contentType(MediaType.APPLICATION_JSON).content(submitBody()))
                .andExpect(status().isUnauthorized());
    }

    private InterviewAiFeedbackResponse feedbacks() {
        return new InterviewAiFeedbackResponse(List.of(
                new InterviewAiFeedbackResponse.Feedback("q1", 80, "TTL 언급은 좋았습니다."),
                new InterviewAiFeedbackResponse.Feedback("q2", 70, "분산 락 사례가 더 필요합니다."),
                new InterviewAiFeedbackResponse.Feedback("q3", 90, "fallback 전략이 구체적입니다.")));
    }

    private String submitBody() {
        return "{\"answers\":[" + String.join(",",
                answerJson("q1", "Redis 캐시 전략을 설명해 주세요.", "평가 의도", "TTL", "TTL로 관리합니다."),
                answerJson("q2", "분산 락은 언제 필요합니까?", "평가 의도", "경합", "재고 차감 시 사용합니다."),
                answerJson("q3", "캐시 장애에 어떻게 대응합니까?", "평가 의도", "fallback", "원본 DB로 우회합니다.")) + "]}";
    }

    private String answerJson(String id, String question, String intent, String keyPoint, String answer) {
        return "{\"id\":\"" + id + "\",\"question\":\"" + question + "\",\"intent\":\"" + intent
                + "\",\"keyPoints\":[\"" + keyPoint + "\"],\"answer\":\"" + answer + "\"}";
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
