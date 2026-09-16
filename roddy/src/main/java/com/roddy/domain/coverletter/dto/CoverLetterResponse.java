package com.roddy.domain.coverletter.dto;

import com.roddy.domain.coverletter.entity.CoverLetter;
import com.roddy.domain.jobposting.entity.JobPosting;
import java.time.LocalDateTime;
import java.util.List;

public record CoverLetterResponse(Long id, String title, Job job, long version,
                                  List<SaveCoverLetterRequest.Answer> answers,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static CoverLetterResponse from(CoverLetter letter) {
        return new CoverLetterResponse(letter.getId(), letter.getTitle(), Job.from(letter.getJobPosting()),
                letter.getVersion(), letter.getAnswers().stream()
                .map(a -> new SaveCoverLetterRequest.Answer(a.getQuestion(), a.getAnswer())).toList(),
                letter.getCreatedAt(), letter.getUpdatedAt());
    }

    public record Job(Long id, String title, String company) {
        public static Job from(JobPosting job) {
            return job == null ? null : new Job(job.getId(), job.getTitle(), job.getCompany());
        }
    }
}
