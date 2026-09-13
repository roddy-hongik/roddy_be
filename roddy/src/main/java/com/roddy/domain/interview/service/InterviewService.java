package com.roddy.domain.interview.service;

import com.roddy.domain.analysis.service.CompetencyGapReader;
import com.roddy.domain.analysis.service.CompetencyGapReader.CompetencyGap;
import com.roddy.domain.interview.dto.InterviewQuestionsResponse;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import com.roddy.global.client.interview.InterviewAiClient;
import com.roddy.global.client.interview.InterviewAiRequest;
import com.roddy.global.client.interview.InterviewAiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final int QUESTION_COUNT = 3;

    private final CompetencyGapReader competencyGapReader;
    private final InterviewAiClient interviewAiClient;

    @Transactional(readOnly = true)
    public InterviewQuestionsResponse generateQuestions(Long userId) {
        CompetencyGap context = competencyGapReader.read(userId);
        if (context.gapSkills().isEmpty()) {
            throw new GeneralException(GeneralErrorCode.INTERVIEW_GAP_EMPTY);
        }

        InterviewAiResponse generated = interviewAiClient.generate(new InterviewAiRequest(
                context.currentSkills(), context.gapSkills(),
                context.targetJob().getDescription(), context.targetCompany()));
        validate(generated);
        return InterviewQuestionsResponse.from(generated);
    }

    private void validate(InterviewAiResponse response) {
        if (response == null || response.questions() == null
                || response.questions().size() != QUESTION_COUNT) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE);
        }
        boolean invalid = response.questions().stream().anyMatch(question ->
                question == null || isBlank(question.id()) || isBlank(question.question())
                        || isBlank(question.intent()) || question.keyPoints() == null
                        || question.keyPoints().isEmpty()
                        || question.keyPoints().stream().anyMatch(this::isBlank));
        if (invalid) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE);
        }
        boolean duplicatedIds = response.questions().stream()
                .map(InterviewAiResponse.Question::id)
                .distinct().count() != QUESTION_COUNT;
        boolean duplicatedQuestions = response.questions().stream()
                .map(question -> question.question().trim().toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(HashSet::new)).size() != QUESTION_COUNT;
        if (duplicatedIds || duplicatedQuestions) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
