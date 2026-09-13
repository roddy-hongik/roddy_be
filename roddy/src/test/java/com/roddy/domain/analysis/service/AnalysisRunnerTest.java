package com.roddy.domain.analysis.service;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.enums.ExperienceLevel;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.domain.notification.NotificationService;
import com.roddy.global.client.analysis.AnalysisAiClient;
import com.roddy.global.client.analysis.AnalysisAiRequest;
import com.roddy.global.client.analysis.AnalysisAiResponse;
import com.roddy.global.config.s3.S3ObjectUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AnalysisRunnerTest {

    private static final Long USER_ID = 1L;
    private static final Long REPORT_ID = 10L;

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AnalysisAiClient analysisAiClient = mock(AnalysisAiClient.class);
    private final AnalysisReportStore analysisReportStore = mock(AnalysisReportStore.class);
    private final S3ObjectUrlService s3ObjectUrlService = mock(S3ObjectUrlService.class);
    private final NotificationService notificationService = mock(NotificationService.class);

    private final AnalysisRunner analysisRunner = new AnalysisRunner(
            userRepository, analysisAiClient, analysisReportStore, s3ObjectUrlService,
            new CompetencyCategoryCatalog(), notificationService);

    @BeforeEach
    void setUp() {
        // 사용자는 지금 프론트엔드를 희망한다.
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user(DesiredJob.FRONTEND)));
        given(s3ObjectUrlService.createPresignedGetUrl(anyString()))
                .willReturn("https://s3.example.com/portfolio.pdf?signed");
        given(analysisAiClient.analyze(any())).willReturn(emptyResponse());
    }

    @Test
    @DisplayName("리포트에 남긴 직무의 평가 축을 AI 서버에 넘긴다")
    void sendsCategoriesOfReportJob() {
        // 백엔드로 분석을 요청한 뒤 프론트엔드로 직무를 바꿨어도, 그 리포트는 백엔드 축으로 채점한다.
        given(analysisReportStore.findDesiredJob(REPORT_ID)).willReturn(DesiredJob.BACKEND);

        analysisRunner.run(USER_ID, REPORT_ID);

        AnalysisAiRequest request = captureRequest();
        assertThat(request.desiredJob()).isEqualTo("BACKEND");
        assertThat(request.categories())
                .extracting(AnalysisAiRequest.Category::code)
                .containsExactly("DATA_MODELING", "ARCHITECTURE", "SCALABILITY", "STABILITY", "DEVOPS_CICD", "MONITORING");
        verify(analysisReportStore).complete(eq(REPORT_ID), any());
        verify(notificationService).createGrowthReport(USER_ID, REPORT_ID);
    }

    @Test
    @DisplayName("축을 정하지 않은 직무면 축 없이 분석을 요청한다")
    void sendsNoCategoriesForUndefinedJob() {
        given(analysisReportStore.findDesiredJob(REPORT_ID)).willReturn(DesiredJob.FRONTEND);

        analysisRunner.run(USER_ID, REPORT_ID);

        assertThat(captureRequest().categories()).isEmpty();
    }

    private AnalysisAiRequest captureRequest() {
        ArgumentCaptor<AnalysisAiRequest> captor = ArgumentCaptor.forClass(AnalysisAiRequest.class);
        verify(analysisAiClient).analyze(captor.capture());
        return captor.getValue();
    }

    private User user(DesiredJob desiredJob) {
        User user = User.builder()
                .email("runner@example.com")
                .password("encoded-password")
                .socialType(SocialType.LOCAL)
                .nickname("분석유저")
                .username("분석유저")
                .role(Role.USER)
                .build();
        user.completeProfile("분석유저", 27, ExperienceLevel.JUNIOR, desiredJob,
                "portfolio/1/portfolio.pdf", "portfolio.pdf", LocalDateTime.now());
        return user;
    }

    private AnalysisAiResponse emptyResponse() {
        return new AnalysisAiResponse("제목", 0, "요약", "", "", List.of(), List.of(),
                new AnalysisAiResponse.Sources(0, false, List.of()));
    }
}
