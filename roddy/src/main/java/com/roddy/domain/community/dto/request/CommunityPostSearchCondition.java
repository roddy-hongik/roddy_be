package com.roddy.domain.community.dto.request;

import com.roddy.domain.community.enums.CommunityJobCategory;
import com.roddy.domain.community.enums.CommunityPostCategory;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommunityPostSearchCondition {

    private CommunityPostCategory postCategory;
    private CommunityJobCategory jobCategory;
    private String keyword;
    private String company;
    private String position;
    private String techStack;
}
