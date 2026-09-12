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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

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

    @Transactional
    public ToggleJobScrapResponse toggleScrap(Long jobPostingId, Long userId) {
        JobPosting posting = findPosting(jobPostingId);

        Optional<JobBookmark> scrapped = jobBookmarkRepository.findByUserIdAndJobPostingId(userId, jobPostingId);
        if (scrapped.isPresent()) {
            jobBookmarkRepository.delete(scrapped.get());
            return ToggleJobScrapResponse.of(jobPostingId, false);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        jobBookmarkRepository.save(JobBookmark.create(user, posting));
        return ToggleJobScrapResponse.of(jobPostingId, true);
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
