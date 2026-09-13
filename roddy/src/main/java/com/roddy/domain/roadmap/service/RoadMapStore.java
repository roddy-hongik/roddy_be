package com.roddy.domain.roadmap.service;

import com.roddy.domain.RoadMap;
import com.roddy.domain.analysis.service.CompetencyGapReader;
import com.roddy.domain.analysis.service.CompetencyGapReader.CompetencyGap;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.roadmap.dto.RoadMapSummaryResponse;
import com.roddy.domain.roadmap.dto.SavedRoadMapResponse;
import com.roddy.domain.roadmap.repository.RoadMapRepository;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.function.Function;

/**
 * 로드맵의 조회와 저장. 트랜잭션은 여기서만 연다.
 *
 * <p>{@link RoadMapService} 가 AI 호출과 중복 저장 충돌을 트랜잭션 밖에서 다룰 수 있도록 나눠 두었다.
 */
@Service
@RequiredArgsConstructor
public class RoadMapStore {

    private final UserRepository userRepository;
    private final CompetencyGapReader competencyGapReader;
    private final RoadMapRepository roadMapRepository;

    /**
     * 최신 분석의 기술과, 같은 직무의 모집 중 공고가 자주 요구하지만 아직 없는 기술.
     *
     * <p>엔티티 없이 값만 담아 돌려주므로 트랜잭션이 끝난 뒤에 AI 를 부를 때도 그대로 쓸 수 있다.
     */
    @Transactional(readOnly = true)
    public RoadMapSummaryResponse readSummary(Long userId) {
        CompetencyGap gap = competencyGapReader.read(userId);
        return new RoadMapSummaryResponse(
                gap.currentSkills(), gap.gapSkills(), gap.targetJob().getDescription(), gap.targetCompany());
    }

    @Transactional(readOnly = true)
    public Optional<SavedRoadMapResponse> findByFingerprint(Long userId, String fingerprint) {
        return roadMapRepository.findFirstByUserIdAndFingerprint(userId, fingerprint)
                .map(SavedRoadMapResponse::from);
    }

    /** 유니크 제약에 걸리면 이 트랜잭션만 롤백하고 예외를 그대로 던진다. 중복인지는 부른 쪽이 다시 조회해 가린다. */
    @Transactional
    public SavedRoadMapResponse create(Long userId, Function<User, RoadMap> roadMapOf) {
        return SavedRoadMapResponse.from(roadMapRepository.saveAndFlush(roadMapOf.apply(requireUser(userId))));
    }

    /** 최신순으로 고정한다. 요청의 sort 를 붙이면 모르는 필드 이름에서 조회가 깨진다. */
    @Transactional(readOnly = true)
    public Page<SavedRoadMapResponse> findSaved(Long userId, Pageable pageable) {
        requireUser(userId);
        return roadMapRepository.findAllByUserIdOrderByIdDesc(
                        userId, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))
                .map(SavedRoadMapResponse::from);
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }
}
