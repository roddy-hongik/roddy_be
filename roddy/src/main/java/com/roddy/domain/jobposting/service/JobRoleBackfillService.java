package com.roddy.domain.jobposting.service;

import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.jobposting.entity.JobPosting;
import com.roddy.domain.jobposting.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobRoleBackfillService {
    private final JobPostingRepository repository;

    /** 기존 분류는 유지하며 미분류 공고만 200건씩 보정한다. 과거 공고 알림은 생성하지 않는다. */
    @Transactional
    public Result backfill(long afterId) {
        List<JobPosting> postings = repository.findTop200ByIdGreaterThanAndDesiredJobIsNullOrderByIdAsc(afterId);
        int classified = 0;
        for (JobPosting posting : postings) {
            DesiredJob job = JobRoleClassifier.classify(posting.getTitle(), posting.getRecruitField());
            if (job != null) {
                posting.updateDesiredJob(job);
                classified++;
            }
        }
        long nextAfterId = postings.isEmpty() ? afterId : postings.get(postings.size() - 1).getId();
        return new Result(postings.size(), classified, postings.size() - classified, nextAfterId,
                postings.size() == 200);
    }

    public record Result(int scanned, int classified, int unclassified, long nextAfterId, boolean hasMore) {}
}
