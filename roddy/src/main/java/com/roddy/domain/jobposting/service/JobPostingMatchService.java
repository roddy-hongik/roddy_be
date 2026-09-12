package com.roddy.domain.jobposting.service;

import com.roddy.domain.analysis.service.UserTechStackReader;
import com.roddy.domain.jobposting.dto.response.JobPostingMatchResponse;
import com.roddy.domain.jobposting.dto.response.JobPostingMatchResponse.JobMatchStackResponse;
import com.roddy.domain.jobposting.entity.JobPosting;
import com.roddy.domain.jobposting.repository.JobPostingRepository;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 공고가 요구하는 기술과 사용자가 가진 기술을 견줘 적합도를 낸다.
 *
 * <p>공고 쪽 기술은 본문에서 뽑아낸 값이고, 사용자 쪽 기술은 분석 리포트가 남긴 값이다. 둘 다
 * 사전의 표준 이름으로 맞춘 뒤 견준다. 사용자 기술이 "자바"로 적혀 있어도 공고의 "Java"와 이어진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostingMatchService {

    private static final int NO_SCORE = 0;

    private final JobPostingRepository jobPostingRepository;
    private final UserTechStackReader userTechStackReader;

    public JobPostingMatchResponse getMatch(Long jobPostingId, Long userId) {
        JobPosting posting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.JOB_POSTING_NOT_FOUND));

        Map<String, Integer> userScores = userTechStackReader.read(userId);
        List<JobMatchStackResponse> stacks = toStacks(posting, userScores);
        List<String> missing = stacks.stream()
                .filter(stack -> !stack.held())
                .map(JobMatchStackResponse::name)
                .toList();

        return new JobPostingMatchResponse(
                jobPostingId,
                MatchRateCalculator.calculate(posting.getTechStacks(), userScores),
                stacks.size(),
                stacks.size() - missing.size(),
                userScores.size(),
                stacks,
                missing
        );
    }

    /** 가진 기술을 앞에 둔다. 무엇이 되고 무엇이 모자란지 순서만 봐도 보이게 하기 위함이다. */
    private List<JobMatchStackResponse> toStacks(JobPosting posting, Map<String, Integer> userScores) {
        return posting.getTechStacks().stream()
                .map(name -> new JobMatchStackResponse(
                        name, userScores.getOrDefault(name, NO_SCORE), userScores.containsKey(name)))
                .sorted(Comparator.comparingInt(JobMatchStackResponse::userScore).reversed()
                        .thenComparing(JobMatchStackResponse::name))
                .toList();
    }

}
