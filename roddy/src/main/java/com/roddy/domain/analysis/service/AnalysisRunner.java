package com.roddy.domain.analysis.service;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
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

    @Async("analysisExecutor")
    public void run(Long userId) {
        try {
            AnalysisAiResponse response = analysisAiClient.analyze(buildRequest(userId));
            analysisReportStore.complete(userId, response);

            log.info("역량 분석을 마쳤습니다. userId={} 기술={}건", userId, response.stacks().size());
        } catch (Exception e) {
            // 어떤 이유로 실패했는지 사용자에게 보여 줘야 해서 상태를 남기고 끝낸다.
            log.error("역량 분석에 실패했습니다. userId={}", userId, e);
            analysisReportStore.fail(userId, "%s: %s".formatted(e.getClass().getSimpleName(), e.getMessage()));
        }
    }

    /** 읽는 값이 모두 단순 컬럼이라 트랜잭션을 따로 열지 않는다. */
    private AnalysisAiRequest buildRequest(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

        return new AnalysisAiRequest(
                userId,
                user.getGithubUrl(),
                user.getGithubAccessToken(),
                portfolioUrl(user),
                user.getPortfolioFileName(),
                user.getDesiredJob() == null ? null : user.getDesiredJob().name(),
                user.getExperienceYears() == null ? null : user.getExperienceYears().name()
        );
    }

    /** AI 서버는 S3 자격증명을 갖지 않는다. 읽을 수 있는 주소를 여기서 만들어 건넨다. */
    private String portfolioUrl(User user) {
        String objectKey = user.getPortfolioObjectKey();
        return (objectKey == null || objectKey.isBlank())
                ? null
                : s3ObjectUrlService.createPresignedGetUrl(objectKey);
    }
}
