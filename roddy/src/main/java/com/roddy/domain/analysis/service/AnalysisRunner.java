package com.roddy.domain.analysis.service;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import com.roddy.global.client.analysis.AnalysisAiClient;
import com.roddy.global.client.analysis.AnalysisAiRequest;
import com.roddy.global.client.analysis.AnalysisAiResponse;
import com.roddy.global.config.s3.S3ObjectUrlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 분석을 실제로 돌린다.
 *
 * <p>깃허브를 훑고 LLM 을 부르느라 수십 초가 걸리므로 요청을 받은 스레드에서 기다리지 않는다.
 * 사용자는 먼저 "분석 중"을 보고, 끝나면 조회로 결과를 본다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisRunner {

    private final UserRepository userRepository;
    private final AnalysisAiClient analysisAiClient;
    private final AnalysisReportStore analysisReportStore;
    private final S3ObjectUrlService s3ObjectUrlService;
    private final CompetencyCategoryCatalog competencyCategoryCatalog;

    /**
     * @param reportId 미리 진행 중으로 만들어 둔 리포트. 결과는 이 리포트에 채운다
     */
    @Async("analysisExecutor")
    public void run(Long userId, Long reportId) {
        try {
            AnalysisAiResponse response = analysisAiClient.analyze(buildRequest(userId, reportId));
            analysisReportStore.complete(reportId, response);

            log.info("역량 분석을 마쳤습니다. userId={} reportId={} 기술={}건",
                    userId, reportId, response.stacks().size());
        } catch (Exception e) {
            // 어떤 이유로 실패했는지 사용자에게 보여 줘야 해서 상태를 남기고 끝낸다.
            log.error("역량 분석에 실패했습니다. userId={} reportId={}", userId, reportId, e);
            analysisReportStore.fail(reportId, "%s: %s".formatted(e.getClass().getSimpleName(), e.getMessage()));
        }
    }

    private AnalysisAiRequest buildRequest(Long userId, Long reportId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        // 요청한 뒤에 직무를 바꿨더라도 리포트에 남긴 직무로 채점한다. 그래야 리포트의 직무와 축이 어긋나지 않는다.
        DesiredJob desiredJob = analysisReportStore.findDesiredJob(reportId);

        return new AnalysisAiRequest(
                userId,
                user.getGithubUrl(),
                user.getGithubAccessToken(),
                portfolioUrl(user),
                user.getPortfolioFileName(),
                desiredJob == null ? null : desiredJob.name(),
                user.getExperienceYears() == null ? null : user.getExperienceYears().name(),
                categories(desiredJob)
        );
    }

    private List<AnalysisAiRequest.Category> categories(DesiredJob desiredJob) {
        return competencyCategoryCatalog.categoriesOf(desiredJob).stream()
                .map(category -> new AnalysisAiRequest.Category(
                        category.code(), category.name(), category.description()))
                .toList();
    }

    /** AI 서버는 S3 자격증명을 갖지 않는다. 읽을 수 있는 주소를 여기서 만들어 건넨다. */
    private String portfolioUrl(User user) {
        String objectKey = user.getPortfolioObjectKey();
        return (objectKey == null || objectKey.isBlank())
                ? null
                : s3ObjectUrlService.createPresignedGetUrl(objectKey);
    }
}
