package com.roddy.domain.community.dto.request;

import com.roddy.domain.community.enums.CommunityJobCategory;
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

    private String company;

    private String position;

    private List<String> techStacks;

    private List<MultipartFile> images;
}
