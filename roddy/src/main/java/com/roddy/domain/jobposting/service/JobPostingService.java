package com.roddy.domain.jobposting.service;

import com.roddy.domain.analysis.service.UserTechStackReader;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.enums.JobPostingStatus;
import com.roddy.domain.jobposting.dto.request.JobPostingSearchCondition;
import com.roddy.domain.jobposting.dto.response.JobPostingDetailResponse;
import com.roddy.domain.jobposting.dto.response.JobPostingListItemResponse;
import com.roddy.domain.jobposting.dto.response.JobPostingListResponse;
import com.roddy.domain.jobposting.dto.response.ToggleJobScrapResponse;
import com.roddy.domain.jobposting.entity.JobBookmark;
import com.roddy.domain.jobposting.entity.JobPosting;
import com.roddy.domain.jobposting.repository.JobBookmarkRepository;
import com.roddy.domain.jobposting.repository.JobPostingRepository;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingService {

    /** 매칭률 순 정렬을 요청할 때 쓰는 값. 그 외에는 게시일 최신순이다. */
    private static final String SORT_BY_MATCH = "match";

    private final JobPostingRepository jobPostingRepository;
    private final JobBookmarkRepository jobBookmarkRepository;
    private final UserRepository userRepository;
    private final UserTechStackReader userTechStackReader;

    public JobPostingListResponse getJobPostings(JobPostingSearchCondition condition,
                                                 int page, int size, String sort, Long userId) {
        Map<String, Integer> userScores = userTechStackReader.read(userId);

        // 분석 결과가 없으면 매칭률이 모두 비어 정렬할 것이 없다. 그때는 최신순을 그대로 쓴다.
        if (SORT_BY_MATCH.equalsIgnoreCase(sort) && !userScores.isEmpty()) {
            return getJobPostingsByMatchRate(condition, page, size, userId, userScores);
        }
        return getJobPostingsByLatest(condition, page, size, userId, userScores);
    }

    private JobPostingListResponse getJobPostingsByLatest(JobPostingSearchCondition condition,
                                                         int page, int size, Long userId,
                                                         Map<String, Integer> userScores) {
        Page<JobPosting> found = jobPostingRepository.search(
                statusOf(condition),
                blankToNull(condition.getCompany()),
                condition.getRecruitType(),
                keywordOf(condition),
                PageRequest.of(page, size)
        );

        return JobPostingListResponse.of(found, toItems(found.getContent(), userId, userScores));
    }

    /**
     * 매칭률이 높은 공고를 앞에 둔다.
     *
     * <p>페이지를 나누려면 전체를 견줘 봐야 하므로 조건에 맞는 id 를 모두 가져와 점수를 매긴 뒤
     * 필요한 페이지만 다시 읽는다. 공고 본문까지 전부 읽으면 무겁기 때문에 id 와 요구 기술만 본다.
     */
    private JobPostingListResponse getJobPostingsByMatchRate(JobPostingSearchCondition condition,
                                                            int page, int size, Long userId,
                                                            Map<String, Integer> userScores) {
        List<Long> candidateIds = jobPostingRepository.searchIds(
                statusOf(condition),
                blankToNull(condition.getCompany()),
                condition.getRecruitType(),
                keywordOf(condition)
        );

        List<Long> orderedIds = orderByMatchRate(candidateIds, userScores);
        List<Long> pageIds = slice(orderedIds, page, size);
        List<JobPosting> postings = sortByIdOrder(jobPostingRepository.findAllById(pageIds), pageIds);

        return new JobPostingListResponse(
                toItems(postings, userId, userScores),
                page,
                size,
                orderedIds.size(),
                totalPages(orderedIds.size(), size)
        );
    }

    private List<Long> orderByMatchRate(List<Long> candidateIds, Map<String, Integer> userScores) {
        Map<Long, List<String>> stacksByPosting = techStacksByPosting(candidateIds);

        // 점수가 같으면 원래 순서(최신순)를 그대로 둔다. 자바의 정렬은 순서를 흔들지 않는다.
        return candidateIds.stream()
                .sorted(Comparator.comparingInt(
                        (Long id) -> matchRateOrZero(stacksByPosting.get(id), userScores)).reversed())
                .toList();
    }

    private Map<Long, List<String>> techStacksByPosting(List<Long> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<String>> stacks = new LinkedHashMap<>();
        for (Object[] row : jobPostingRepository.findTechStacksByIds(candidateIds)) {
            stacks.computeIfAbsent((Long) row[0], key -> new ArrayList<>()).add((String) row[1]);
        }
        return stacks;
    }

    private int matchRateOrZero(List<String> requiredStacks, Map<String, Integer> userScores) {
        Integer matchRate = MatchRateCalculator.calculate(requiredStacks, userScores);
        return matchRate == null ? 0 : matchRate;
    }

    private List<JobPostingListItemResponse> toItems(List<JobPosting> postings, Long userId,
                                                     Map<String, Integer> userScores) {
        Set<Long> scrappedIds = scrappedIds(userId, postings);

        return postings.stream()
                .map(posting -> JobPostingListItemResponse.from(
                        posting,
                        MatchRateCalculator.calculate(posting.getTechStacks(), userScores),
                        scrappedIds.contains(posting.getId())))
                .toList();
    }

    /**
     * 요청한 페이지에 해당하는 부분만 잘라낸다.
     *
     * <p>페이지 번호에는 상한이 없어서 page * size 가 int 범위를 넘을 수 있다. 그대로 두면 음수가
     * 되어 subList 가 터지므로 long 으로 계산한 뒤 목록 크기에 맞춘다. 범위를 넘어선 페이지는 빈
     * 목록이 된다.
     */
    private List<Long> slice(List<Long> ids, int page, int size) {
        int from = (int) Math.min((long) page * size, ids.size());
        int to = (int) Math.min((long) from + size, ids.size());
        return ids.subList(from, to);
    }

    /** findAllById 는 순서를 지켜 주지 않는다. 매긴 순서대로 다시 놓는다. */
    private List<JobPosting> sortByIdOrder(List<JobPosting> postings, List<Long> orderedIds) {
        Map<Long, JobPosting> byId = postings.stream()
                .collect(java.util.stream.Collectors.toMap(JobPosting::getId, posting -> posting));

        return orderedIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    private int totalPages(int totalElements, int size) {
        return (int) Math.ceil((double) totalElements / size);
    }

    public JobPostingDetailResponse getJobPosting(Long jobPostingId, Long userId) {
        JobPosting posting = findPosting(jobPostingId);
        boolean scrapped = userId != null
                && jobBookmarkRepository.existsByUserIdAndJobPostingId(userId, jobPostingId);

        return JobPostingDetailResponse.from(posting, scrapped);
    }

    public List<JobPostingListItemResponse> getMyScrappedJobPostings(Long userId) {
        return jobBookmarkRepository.findScrappedJobPostings(userId).stream()
                .map(posting -> JobPostingListItemResponse.from(posting, null, true))
                .toList();
    }

    /**
     * 스크랩을 담거나 해제한다.
     *
     * <p>같은 공고에 대한 요청이 동시에 들어오면 둘 다 "없음"을 보고 함께 담으려 하거나, 함께 해제하려
     * 한다. 이때 {@code uk_job_bookmark_user_job} 제약이나 이미 지워진 행 때문에 한쪽이 실패하는데,
     * 어느 쪽이든 사용자가 원한 상태는 이미 만들어져 있으므로 그 상태를 그대로 돌려준다.
     */
    @Transactional
    public ToggleJobScrapResponse toggleScrap(Long jobPostingId, Long userId) {
        JobPosting posting = findPosting(jobPostingId);

        Optional<JobBookmark> scrapped = jobBookmarkRepository.findByUserIdAndJobPostingId(userId, jobPostingId);
        if (scrapped.isPresent()) {
            return removeScrap(jobPostingId, scrapped.get());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        return addScrap(jobPostingId, user, posting);
    }

    private ToggleJobScrapResponse addScrap(Long jobPostingId, User user, JobPosting posting) {
        try {
            jobBookmarkRepository.save(JobBookmark.create(user, posting));
        } catch (DataIntegrityViolationException e) {
            // 다른 요청이 먼저 담았다. 이미 스크랩된 상태다.
            log.debug("이미 스크랩된 공고입니다. userId={} jobPostingId={}", user.getId(), jobPostingId);
        }
        return ToggleJobScrapResponse.of(jobPostingId, true);
    }

    private ToggleJobScrapResponse removeScrap(Long jobPostingId, JobBookmark scrapped) {
        try {
            jobBookmarkRepository.delete(scrapped);
        } catch (OptimisticLockingFailureException e) {
            // 다른 요청이 먼저 해제했다. 이미 지워진 상태다.
            log.debug("이미 해제된 스크랩입니다. jobBookmarkId={}", scrapped.getId());
        }
        return ToggleJobScrapResponse.of(jobPostingId, false);
    }

    private JobPosting findPosting(Long jobPostingId) {
        return jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.JOB_POSTING_NOT_FOUND));
    }

    /** 로그인하지 않았거나 목록이 비어 있으면 스크랩을 조회하지 않는다. */
    private Set<Long> scrappedIds(Long userId, List<JobPosting> postings) {
        if (userId == null || postings.isEmpty()) {
            return Set.of();
        }
        List<Long> postingIds = postings.stream().map(JobPosting::getId).toList();
        return Set.copyOf(jobBookmarkRepository.findScrappedJobPostingIds(userId, postingIds));
    }

    private JobPostingStatus statusOf(JobPostingSearchCondition condition) {
        return condition.getStatus() == null ? JobPostingStatus.OPEN : condition.getStatus();
    }

    private String keywordOf(JobPostingSearchCondition condition) {
        String keyword = blankToNull(condition.getKeyword());
        return keyword == null ? null : "%" + keyword.toLowerCase(Locale.ROOT) + "%";
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
