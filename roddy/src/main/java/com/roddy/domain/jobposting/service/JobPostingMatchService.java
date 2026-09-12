package com.roddy.domain.jobposting.service;

import com.roddy.domain.analysis.entity.UserStack;
import com.roddy.domain.analysis.repository.UserStackRepository;
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
import java.util.HashMap;
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

    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;

    private final JobPostingRepository jobPostingRepository;
    private final UserStackRepository userStackRepository;
    private final TechStackExtractor techStackExtractor;

    public JobPostingMatchResponse getMatch(Long jobPostingId, Long userId) {
        JobPosting posting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.JOB_POSTING_NOT_FOUND));

        Map<String, Integer> userScores = userScores(userId);
        List<JobMatchStackResponse> stacks = toStacks(posting, userScores);
        List<String> missing = stacks.stream()
                .filter(stack -> !stack.held())
                .map(JobMatchStackResponse::name)
                .toList();

        return new JobPostingMatchResponse(
                jobPostingId,
                matchRate(stacks, userScores.size()),
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
                        name, userScores.getOrDefault(name, MIN_SCORE), userScores.containsKey(name)))
                .sorted(Comparator.comparingInt(JobMatchStackResponse::userScore).reversed()
                        .thenComparing(JobMatchStackResponse::name))
                .toList();
    }

    /**
     * 공고가 요구하는 기술마다 사용자의 점수를 더해 평균 낸다. 갖고 있지 않은 기술은 0 점이므로
     * 요구 기술을 많이 가질수록, 그리고 깊이 알수록 높아진다.
     *
     * <p>견줄 것이 없으면 숫자를 만들지 않는다. 0 을 주면 "적합하지 않다"로 읽히는데, 실제로는
     * 아직 판단할 근거가 없다는 뜻이기 때문이다.
     */
    private Integer matchRate(List<JobMatchStackResponse> stacks, int userStackCount) {
        if (stacks.isEmpty() || userStackCount == 0) {
            return null;
        }
        int total = stacks.stream().mapToInt(JobMatchStackResponse::userScore).sum();
        return Math.round((float) total / stacks.size());
    }

    /** 같은 기술이 여러 번 나오면 높은 점수를 남긴다. */
    private Map<String, Integer> userScores(Long userId) {
        Map<String, Integer> scores = new HashMap<>();

        for (UserStack userStack : userStackRepository.findAllWithStackDetailByUserId(userId)) {
            String name = techStackExtractor.canonicalize(userStack.getStackDetail().getStackName());
            if (name == null) {
                continue;
            }
            scores.merge(name, Math.clamp(userStack.getScore(), MIN_SCORE, MAX_SCORE), Math::max);
        }
        return scores;
    }
}
