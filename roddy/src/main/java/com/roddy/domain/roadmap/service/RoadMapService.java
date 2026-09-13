package com.roddy.domain.roadmap.service;

import com.roddy.domain.RoadMap;
import com.roddy.domain.analysis.service.CompetencyGapReader;
import com.roddy.domain.analysis.service.CompetencyGapReader.CompetencyGap;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoadMapService {

    private static final List<String> STAGE_ORDER = List.of("기초", "심화", "실전 프로젝트");

    private final UserRepository userRepository;
    private final CompetencyGapReader competencyGapReader;
    private final RoadMapRepository roadMapRepository;
    private final RoadMapAiClient roadMapAiClient;

    @Transactional(readOnly = true)
    public RoadMapSummaryResponse getSummary(Long userId) {
        return toResponse(competencyGapReader.read(userId));
    }

    @Transactional(readOnly = true)
    public GeneratedRoadMapResponse generate(Long userId) {
        CompetencyGap context = competencyGapReader.read(userId);
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
        CompetencyGap context = competencyGapReader.read(userId);
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

    private SaveRoadMapResponse saveNew(CompetencyGap context, SaveRoadMapRequest request, String fingerprint) {
        RoadMap roadMap = RoadMap.create(
                context.user(), request.title().trim(), context.targetJob(), context.targetCompany(),
                context.currentSkills(), context.gapSkills(), fingerprint);
        request.steps().forEach(step -> roadMap.addStep(
                step.stage(), step.goal().trim(), normalized(step.topics()), normalized(step.outputs())));

        return SaveRoadMapResponse.saved(SavedRoadMapResponse.from(roadMapRepository.saveAndFlush(roadMap)));
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

    private String fingerprint(CompetencyGap context, SaveRoadMapRequest request) {
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

    private RoadMapSummaryResponse toResponse(CompetencyGap context) {
        return new RoadMapSummaryResponse(
                context.currentSkills(), context.gapSkills(),
                context.targetJob().getDescription(), context.targetCompany());
    }
}
