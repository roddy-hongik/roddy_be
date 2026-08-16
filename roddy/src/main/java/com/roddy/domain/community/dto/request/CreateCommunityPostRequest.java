package com.roddy.domain.community.dto.request;

import com.roddy.domain.community.enums.CommunityJobCategory;
import com.roddy.domain.community.enums.CommunityInterviewSubtype;
import com.roddy.domain.community.enums.CommunityPostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Getter
@Setter
public class CreateCommunityPostRequest {

    @NotNull
    private CommunityPostCategory postCategory;

    @NotNull
    private CommunityJobCategory jobCategory;

    @NotBlank
    private String title;

    @NotBlank
    private String content;

    private List<String> tags;

    private String roadmapId;

    private String roadmapTitle;

    private String summary;

    private String description;

    private String targetJob;

    private String targetCompany;

    private List<String> recommendedSkills;

    /**
     * multipart/form-data에서 단계 정보를 보낼 수 있도록 JSON 문자열로 받습니다.
     */
    private String roadmapStepsJson;

    private CommunityInterviewSubtype interviewSubtype;

    private String company;

    private String jobRole;

    private String position;

    private String preparationPeriod;

    private List<String> techStacks;

    private String processSummary;

    private String background;

    private String preparationProcess;

    private String experienceDetail;

    private String advice;

    private List<MultipartFile> images;
}
