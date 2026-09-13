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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 로드맵의 조회와 저장. 트랜잭션은 여기서만 연다.
 *
 * <p>{@link RoadMapService} 가 AI 호출과 중복 저장 충돌을 트랜잭션 밖에서 다룰 수 있도록 나눠 두었다.
 */
@Service
@RequiredArgsConstructor
public class RoadMapStore {

    private static final int MAX_GAP_SKILLS = 10;

    private final UserRepository userRepository;
    private final DesiredCompanyRepository desiredCompanyRepository;
    private final AnalysisReportStore analysisReportStore;
    private final UserTechStackReader userTechStackReader;
    private final JobPostingRepository jobPostingRepository;
    private final RoadMapRepository roadMapRepository;

    /**
     * 최신 분석의 기술과, 같은 직무의 모집 중 공고가 자주 요구하지만 아직 없는 기술.
     *
     * <p>엔티티 없이 값만 담아 돌려주므로 트랜잭션이 끝난 뒤에 AI 를 부를 때도 그대로 쓸 수 있다.
     */
    @Transactional(readOnly = true)
    public RoadMapSummaryResponse readSummary(Long userId) {
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

        return new RoadMapSummaryResponse(currentSkills, gapSkills, targetJob.getDescription(), targetCompany);
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
