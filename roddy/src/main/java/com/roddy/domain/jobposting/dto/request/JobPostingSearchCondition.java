package com.roddy.domain.jobposting.dto.request;

import com.roddy.domain.enums.JobPostingStatus;
import com.roddy.domain.enums.RecruitType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JobPostingSearchCondition {

    /** 회사명 또는 공고 제목에서 찾는다. */
    private String keyword;

    /** 수집 명세의 회사 코드 (예: kakao). */
    private String company;

    private RecruitType recruitType;

    /** 지정하지 않으면 모집중인 공고만 본다. */
    private JobPostingStatus status;
}
