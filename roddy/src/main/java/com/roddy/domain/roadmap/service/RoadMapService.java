package com.roddy.domain.roadmap.service;

import com.roddy.domain.RoadMap;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.roadmap.dto.GeneratedRoadMapResponse;
import com.roddy.domain.roadmap.dto.RoadMapSummaryResponse;
import com.roddy.domain.roadmap.dto.SaveRoadMapRequest;
import com.roddy.domain.roadmap.dto.SaveRoadMapResponse;
import com.roddy.domain.roadmap.dto.SavedRoadMapListResponse;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import com.roddy.global.client.roadmap.RoadMapAiRequest;
import com.roddy.global.client.roadmap.RoadMapAiResponse;
import com.roddy.global.client.roadmap.RoadMapAiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 로드맵의 바깥쪽 창구.
 *
 * <p>여기서는 트랜잭션을 열지 않는다. AI 서버가 답하는 동안 트랜잭션을 잡고 있지 않고, 같은 로드맵을 동시에
 * 저장하다 유니크 제약에 걸려도 롤백된 트랜잭션 밖에서 다시 조회해 중복으로 답하기 위함이다.
 */
@Service
@RequiredArgsConstructor
public class RoadMapService {

    private static final List<String> STAGE_ORDER = List.of("기초", "심화", "실전 프로젝트");

    private final RoadMapStore roadMapStore;
    private final RoadMapAiClient roadMapAiClient;

    public RoadMapSummaryResponse getSummary(Long userId) {
        return roadMapStore.readSummary(userId);
    }

    public GeneratedRoadMapResponse generate(Long userId) {
        RoadMapSummaryResponse summary = roadMapStore.readSummary(userId);
        if (summary.gapSkills().isEmpty()) {
            throw new GeneralException(GeneralErrorCode.ROADMAP_GAP_EMPTY);
        }

        RoadMapAiResponse generated = roadMapAiClient.generate(new RoadMapAiRequest(
                summary.currentSkills(), summary.gapSkills(), summary.targetJob(), summary.targetCompany()));
        validateGenerated(generated);
        return GeneratedRoadMapResponse.from(
                generated,
                summary.currentSkills(),
                summary.gapSkills(),
                summary.targetJob(),
                summary.targetCompany());
    }

    public SaveRoadMapResponse save(Long userId, SaveRoadMapRequest request) {
        RoadMapContext context = new RoadMapContext(
                resolveTargetJob(request.targetJob()),
                normalizedNullable(request.targetCompany()),
                normalized(request.currentSkills()),
                normalized(request.gapSkills()));
        if (!hasStageOrder(request.steps().stream().map(SaveRoadMapRequest.Step::stage).toList())) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER);
        }
        String fingerprint = fingerprint(context, request);

        return roadMapStore.findByFingerprint(userId, fingerprint)
                .map(SaveRoadMapResponse::duplicate)
                .orElseGet(() -> saveNew(userId, context, request, fingerprint));
    }

    public SavedRoadMapListResponse getSaved(Long userId, Pageable pageable) {
        return SavedRoadMapListResponse.from(roadMapStore.findSaved(userId, pageable));
    }

    /**
     * 같은 로드맵을 동시에 저장하면 둘 다 중복이 없다고 보고 넣다가 한쪽이 유니크 제약에 걸린다.
     * 그때 먼저 저장된 로드맵이 보이면 중복으로 답하고, 보이지 않으면 다른 이유의 실패이므로 그대로 던진다.
     */
    private SaveRoadMapResponse saveNew(Long userId, RoadMapContext context, SaveRoadMapRequest request,
                                        String fingerprint) {
        try {
            return SaveRoadMapResponse.saved(
                    roadMapStore.create(userId, user -> newRoadMap(user, context, request, fingerprint)));
        } catch (DataIntegrityViolationException exception) {
            return roadMapStore.findByFingerprint(userId, fingerprint)
                    .map(SaveRoadMapResponse::duplicate)
                    .orElseThrow(() -> exception);
        }
    }

    private RoadMap newRoadMap(User user, RoadMapContext context, SaveRoadMapRequest request, String fingerprint) {
        RoadMap roadMap = RoadMap.create(
                user, request.title().trim(), context.targetJob(), context.targetCompany(),
                context.currentSkills(), context.gapSkills(), fingerprint);
        request.steps().forEach(step -> roadMap.addStep(
                step.stage(), step.goal().trim(), normalized(step.topics()), normalized(step.outputs())));
        return roadMap;
    }

    /** AI 서버가 약속한 모양을 지키지 않았다. 사용자의 요청 탓이 아니므로 모두 잠시 쓸 수 없다고 답한다. */
    private void validateGenerated(RoadMapAiResponse response) {
        if (response == null || response.title() == null || response.title().isBlank()) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE);
        }
        if (!hasStageOrder(response.steps().stream().map(RoadMapAiResponse.Step::stage).toList())) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE);
        }
        boolean invalidStep = response.steps().stream().anyMatch(step ->
                step.goal() == null || step.goal().isBlank()
                        || invalidValues(step.topics()) || invalidValues(step.outputs()));
        if (invalidStep) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private boolean hasStageOrder(List<String> stages) {
        return STAGE_ORDER.equals(stages);
    }

    private List<String> normalized(List<String> values) {
        return values.stream().map(String::trim).toList();
    }

    private String normalizedNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private DesiredJob resolveTargetJob(String description) {
        return java.util.Arrays.stream(DesiredJob.values())
                .filter(job -> job.getDescription().equals(description.trim()))
                .findFirst()
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_PARAMETER));
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

    private record RoadMapContext(DesiredJob targetJob, String targetCompany,
                                  List<String> currentSkills, List<String> gapSkills) {
    }
}
