package com.roddy.domain.study.dto.request;

import com.roddy.domain.study.enums.StudyMode;
import com.roddy.domain.study.enums.StudyRecruitStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StudySearchCondition {

    private StudyRecruitStatus status;
    private StudyMode mode;
    private String keyword;
}
