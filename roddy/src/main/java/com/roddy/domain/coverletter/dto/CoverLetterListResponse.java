package com.roddy.domain.coverletter.dto;

import com.roddy.domain.coverletter.entity.CoverLetter;
import org.springframework.data.domain.Page;
import java.time.LocalDateTime;
import java.util.List;

public record CoverLetterListResponse(List<Item> coverLetters, int page, int size, long totalElements, int totalPages) {
    public static CoverLetterListResponse from(Page<CoverLetter> page) {
        return new CoverLetterListResponse(page.map(letter -> new Item(letter.getId(), letter.getTitle(),
                CoverLetterResponse.Job.from(letter.getJobPosting()), letter.getUpdatedAt())).getContent(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
    public record Item(Long id, String title, CoverLetterResponse.Job job, LocalDateTime updatedAt) {}
}
