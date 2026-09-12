package com.roddy.domain.jobposting.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record JobPostingListResponse(
        List<JobPostingListItemResponse> jobs,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static JobPostingListResponse of(Page<?> page, List<JobPostingListItemResponse> jobs) {
        return new JobPostingListResponse(
                jobs, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
