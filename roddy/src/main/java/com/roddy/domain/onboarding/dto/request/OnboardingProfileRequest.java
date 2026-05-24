package com.roddy.domain.onboarding.dto.request;

import com.roddy.domain.enums.DesiredJob;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OnboardingProfileRequest {

    @NotBlank
    private String name;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer age;

    @NotNull
    @Min(0)
    @Max(50)
    private Integer experienceYears;

    @NotNull
    private DesiredJob desiredJob;

    @NotBlank
    private String desiredCompany;

    @NotBlank
    private String portfolioObjectKey;

    @NotBlank
    private String portfolioFileName;
}
