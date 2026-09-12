package com.roddy.domain.jobposting.dto.response;

import com.roddy.domain.enums.JobPostingStatus;
import com.roddy.domain.jobposting.entity.JobPosting;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 목록에 뿌릴 공고 한 건.
 *
 * @param experience 인턴 / 신입 / 경력직. 경력 정보를 주지 않는 회사가 많아 비어 있을 수 있다.
 * @param workType   정규직 / 계약직 등 고용 형태.
 */
public record JobPostingListItemResponse(
        Long id,
        String companyCode,
        String company,
        String title,
        String recruitField,
        String experience,
        String workType,
        String location,
        LocalDateTime postedAt,
        LocalDateTime deadline,
        JobPostingStatus status,
        String applyUrl,
        /** 공고 글에서 뽑아낸 요구 기술스택. 사전에 없는 기술은 잡히지 않는다. */
        List<String> techStacks,
        /** 내 기술스택과의 적합도. 아직 분석 결과가 없거나 공고에서 기술을 찾지 못했으면 null. */
        Integer matchRate,
        boolean isScrapped
) {

    public static JobPostingListItemResponse from(JobPosting posting, Integer matchRate, boolean scrapped) {
        return new JobPostingListItemResponse(
                posting.getId(),
                posting.getCompanyCode(),
                posting.getCompany(),
                posting.getTitle(),
                posting.getRecruitField(),
                posting.getRecruitType() == null ? null : posting.getRecruitType().getDisplayName(),
                posting.getEmploymentType(),
                posting.getLocation(),
                posting.getPostedAt(),
                posting.getDeadline(),
                posting.getStatus(),
                posting.getApplyUrl(),
                List.copyOf(posting.getTechStacks()),
                matchRate,
                scrapped
        );
    }
}
