package com.roddy.domain.jobposting.service;

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

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final JobBookmarkRepository jobBookmarkRepository;
    private final UserRepository userRepository;

    public JobPostingListResponse getJobPostings(JobPostingSearchCondition condition,
                                                 int page, int size, Long userId) {
        Page<JobPosting> found = jobPostingRepository.search(
                statusOf(condition),
                blankToNull(condition.getCompany()),
                condition.getRecruitType(),
                keywordOf(condition),
                PageRequest.of(page, size)
        );

        Set<Long> scrappedIds = scrappedIds(userId, found.getContent());
        List<JobPostingListItemResponse> jobs = found.getContent().stream()
                .map(posting -> JobPostingListItemResponse.from(posting, scrappedIds.contains(posting.getId())))
                .toList();

        return JobPostingListResponse.of(found, jobs);
    }

    public JobPostingDetailResponse getJobPosting(Long jobPostingId, Long userId) {
        JobPosting posting = findPosting(jobPostingId);
        boolean scrapped = userId != null
                && jobBookmarkRepository.existsByUserIdAndJobPostingId(userId, jobPostingId);

        return JobPostingDetailResponse.from(posting, scrapped);
    }

    public List<JobPostingListItemResponse> getMyScrappedJobPostings(Long userId) {
        return jobBookmarkRepository.findScrappedJobPostings(userId).stream()
                .map(posting -> JobPostingListItemResponse.from(posting, true))
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
