package com.roddy.domain.mypage.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MyPageProfileUpdateRequest {

    @NotBlank
    private String name;

    @Min(0)
    @Max(100)
    private Integer age;

    private String profileImageUrl;
}
