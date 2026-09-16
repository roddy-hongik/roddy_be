package com.roddy.domain.jobposting.dto.response;

import com.roddy.domain.enums.JobPostingStatus;
import com.roddy.domain.jobposting.entity.JobPosting;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공고 상세.
 *
 * @param content         수집한 공고 본문 원문. 상세 수집이 실패했거나 아직 돌지 않았으면 비어 있다.
 * @param sourceUpdatedAt 채용 사이트가 알려준 최종 수정 시각.
 */
public record JobPostingDetailResponse(
        Long id,
        String companyCode,
        String company,
        String title,
        String content,
        String recruitField,
        String experience,
        String workType,
        String location,
        LocalDateTime postedAt,
        LocalDateTime deadline,
        LocalDateTime sourceUpdatedAt,
        JobPostingStatus status,
        String applyUrl,
        /** 공고 글에서 뽑아낸 요구 기술스택. 사전에 없는 기술은 잡히지 않는다. */
        List<String> techStacks,
        boolean isScrapped
) {

    public static JobPostingDetailResponse from(JobPosting posting, boolean scrapped) {
        return new JobPostingDetailResponse(
                posting.getId(),
                posting.getCompanyCode(),
                posting.getCompany(),
                posting.getTitle(),
                posting.getContent(),
                posting.getRecruitField(),
                posting.getRecruitType() == null ? null : posting.getRecruitType().getDisplayName(),
                posting.getEmploymentType(),
                posting.getLocation(),
                posting.getPostedAt(),
                posting.getDeadline(),
                posting.getSourceUpdatedAt(),
                posting.getStatus(),
                posting.getApplyUrl(),
                List.copyOf(posting.getTechStacks()),
                scrapped
        );
    }
}
