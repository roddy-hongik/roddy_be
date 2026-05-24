package com.roddy.domain.community.dto.request;

import com.roddy.domain.community.enums.CommunityTag;
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
    private CommunityTag tag;

    @NotBlank
    private String title;

    @NotBlank
    private String content;

    private List<MultipartFile> images;
}
