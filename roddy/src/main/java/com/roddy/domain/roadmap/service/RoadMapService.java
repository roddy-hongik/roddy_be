package com.roddy.domain.roadmap.service;

import com.roddy.domain.RoadMap;
import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.analysis.service.AnalysisReportStore;
import com.roddy.domain.analysis.service.UserTechStackReader;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.enums.JobPostingStatus;
import com.roddy.domain.jobposting.repository.JobPostingRepository;
import com.roddy.domain.mypage.entity.DesiredCompany;
import com.roddy.domain.mypage.repository.DesiredCompanyRepository;
import com.roddy.domain.roadmap.dto.GeneratedRoadMapResponse;
import com.roddy.domain.roadmap.dto.RoadMapSummaryResponse;
import com.roddy.domain.roadmap.dto.SaveRoadMapRequest;
import com.roddy.domain.roadmap.dto.SaveRoadMapResponse;
import com.roddy.domain.roadmap.dto.SavedRoadMapResponse;
import com.roddy.domain.roadmap.repository.RoadMapRepository;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import com.roddy.global.client.roadmap.RoadMapAiRequest;
import com.roddy.global.client.roadmap.RoadMapAiResponse;
import com.roddy.global.client.roadmap.RoadMapAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoadMapService {

    private static final int MAX_GAP_SKILLS = 10;
    private static final List<String> STAGE_ORDER = List.of("기초", "심화", "실전 프로젝트");

    private final UserRepository userRepository;
    private final DesiredCompanyRepository desiredCompanyRepository;
    private final AnalysisReportStore analysisReportStore;
    private final UserTechStackReader userTechStackReader;
    private final JobPostingRepository jobPostingRepository;
    private final RoadMapRepository roadMapRepository;
    private final RoadMapAiClient roadMapAiClient;

    @Transactional(readOnly = true)
    public RoadMapSummaryResponse getSummary(Long userId) {
        return context(userId).toResponse();
    }

    @Transactional(readOnly = true)
    public GeneratedRoadMapResponse generate(Long userId) {
        RoadMapContext context = context(userId);
        if (context.gapSkills().isEmpty()) {
            throw new GeneralException(GeneralErrorCode.ROADMAP_GAP_EMPTY);
        }

        RoadMapAiResponse generated = roadMapAiClient.generate(new RoadMapAiRequest(
                context.currentSkills(), context.gapSkills(),
                context.targetJob().getDescription(), context.targetCompany()));
        validateGenerated(generated);
        return GeneratedRoadMapResponse.from(generated);
    }

    @Transactional
    public SaveRoadMapResponse save(Long userId, SaveRoadMapRequest request) {
        RoadMapContext context = context(userId);
        validateStages(request.steps().stream().map(SaveRoadMapRequest.Step::stage).toList());
        String fingerprint = fingerprint(context, request);

        return roadMapRepository.findFirstByUserIdAndFingerprint(userId, fingerprint)
                .map(roadMap -> SaveRoadMapResponse.duplicate(SavedRoadMapResponse.from(roadMap)))
                .orElseGet(() -> saveNew(context, request, fingerprint));
    }

    @Transactional(readOnly = true)
    public List<SavedRoadMapResponse> getSaved(Long userId) {
        requireUser(userId);
        return roadMapRepository.findAllByUserIdOrderByIdDesc(userId).stream()
                .map(SavedRoadMapResponse::from)
                .toList();
    }

    private SaveRoadMapResponse saveNew(RoadMapContext context, SaveRoadMapRequest request, String fingerprint) {
        RoadMap roadMap = RoadMap.create(
                context.user(), request.title().trim(), context.targetJob(), context.targetCompany(),
                context.currentSkills(), context.gapSkills(), fingerprint);
        request.steps().forEach(step -> roadMap.addStep(
                step.stage(), step.goal().trim(), normalized(step.topics()), normalized(step.outputs())));

        return SaveRoadMapResponse.saved(SavedRoadMapResponse.from(roadMapRepository.saveAndFlush(roadMap)));
    }

    private RoadMapContext context(Long userId) {
        User user = requireUser(userId);
        AnalysisReport report = analysisReportStore.findLatestCompleted(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.ANALYSIS_REPORT_NOT_FOUND));
        DesiredJob targetJob = report.getDesiredJob() != null ? report.getDesiredJob() : user.getDesiredJob();
        if (targetJob == null) {
            throw new GeneralException(GeneralErrorCode.ANALYSIS_REPORT_NOT_FOUND);
        }

        Map<String, Integer> scores = userTechStackReader.read(userId);
        List<String> currentSkills = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .toList();
        Set<String> currentKeys = currentSkills.stream()
                .map(skill -> skill.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<String> gapSkills = jobPostingRepository.countRequiredStacks(JobPostingStatus.OPEN, targetJob).stream()
                .map(row -> (String) row[0])
                .filter(skill -> !currentKeys.contains(skill.toLowerCase(Locale.ROOT)))
                .limit(MAX_GAP_SKILLS)
                .toList();
        String targetCompany = desiredCompanyRepository.findByUserId(userId)
                .map(DesiredCompany::getDesiredCompany)
                .orElse(null);

        return new RoadMapContext(user, targetJob, targetCompany, currentSkills, gapSkills);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }

    private void validateGenerated(RoadMapAiResponse response) {
        if (response == null || response.title() == null || response.title().isBlank()) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE);
        }
        validateStages(response.steps().stream().map(RoadMapAiResponse.Step::stage).toList());
        boolean invalidStep = response.steps().stream().anyMatch(step ->
                step.goal() == null || step.goal().isBlank()
                        || invalidValues(step.topics()) || invalidValues(step.outputs()));
        if (invalidStep) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private void validateStages(List<String> stages) {
        if (!STAGE_ORDER.equals(stages)) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }
    }

    private List<String> normalized(List<String> values) {
        return values.stream().map(String::trim).toList();
    }

    private boolean invalidValues(List<String> values) {
        return values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isBlank());
    }

    private String fingerprint(RoadMapContext context, SaveRoadMapRequest request) {
        String canonical = request.title().trim() + "\u0000" + context.targetJob().name() + "\u0000"
                + (context.targetCompany() == null ? "" : context.targetCompany()) + "\u0000"
                + String.join("\u0000", context.currentSkills()) + "\u0000"
                + String.join("\u0000", context.gapSkills()) + "\u0000"
                + request.steps().stream()
                .map(step -> step.stage() + "\u0000" + step.goal().trim() + "\u0000"
                        + String.join("\u0000", normalized(step.topics())) + "\u0000"
                        + String.join("\u0000", normalized(step.outputs())))
                .collect(Collectors.joining("\u0001"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    private record RoadMapContext(User user, DesiredJob targetJob, String targetCompany,
                                  List<String> currentSkills, List<String> gapSkills) {
        private RoadMapSummaryResponse toResponse() {
            return new RoadMapSummaryResponse(currentSkills, gapSkills, targetJob.getDescription(), targetCompany);
        }
    }
}
