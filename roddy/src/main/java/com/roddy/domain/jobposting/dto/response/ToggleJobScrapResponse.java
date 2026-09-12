package com.roddy.domain.jobposting.dto.response;

public record ToggleJobScrapResponse(Long jobPostingId, boolean isScrapped) {

    public static ToggleJobScrapResponse of(Long jobPostingId, boolean scrapped) {
        return new ToggleJobScrapResponse(jobPostingId, scrapped);
    }
}
