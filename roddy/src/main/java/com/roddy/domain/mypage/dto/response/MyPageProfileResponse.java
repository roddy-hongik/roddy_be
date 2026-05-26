package com.roddy.domain.mypage.dto.response;

public record MyPageProfileResponse(
        String name,
        int age,
        String profileImageUrl,
        String desiredJob,
        String desiredCompany,
        String experienceYears,
        String portfolioFileName,
        String portfolioUrl,
        boolean githubConnected
) {
}
