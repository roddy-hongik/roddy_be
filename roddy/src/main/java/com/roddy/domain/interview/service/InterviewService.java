package com.roddy.domain.interview.service;

import com.roddy.domain.analysis.service.CompetencyGapReader;
import com.roddy.domain.analysis.service.CompetencyGapReader.CompetencyGap;
import com.roddy.domain.interview.dto.InterviewQuestionsResponse;
import com.roddy.domain.interview.dto.InterviewSessionListResponse;
import com.roddy.domain.interview.dto.InterviewSessionResponse;
import com.roddy.domain.interview.dto.SubmitInterviewAnswersRequest;
import com.roddy.domain.interview.entity.InterviewAnswer;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import com.roddy.global.client.interview.InterviewAiClient;
import com.roddy.global.client.interview.InterviewAiFeedbackRequest;
import com.roddy.global.client.interview.InterviewAiFeedbackResponse;
import com.roddy.global.client.interview.InterviewAiRequest;
import com.roddy.global.client.interview.InterviewAiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 역량 격차로 모의면접 질문을 만들고, 답변을 채점해 회차로 저장한다.
 *
 * <p>여기서는 트랜잭션을 열지 않는다. 역량 격차는 {@link CompetencyGapReader} 가, 저장과 조회는
 * {@link InterviewSessionStore} 가 각자 자기 트랜잭션에서 처리하고, AI 서버가 답하는 동안에는
 * 트랜잭션을 잡고 있지 않는다.
 */
@Service
@RequiredArgsConstructor
public class InterviewService {

    private static final int QUESTION_COUNT = 3;

    private final CompetencyGapReader competencyGapReader;
    private final InterviewAiClient interviewAiClient;
    private final InterviewSessionStore interviewSessionStore;

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

    public InterviewSessionResponse submitAnswers(Long userId, SubmitInterviewAnswersRequest request) {
        InterviewAiFeedbackResponse generated = interviewAiClient.generateFeedback(new InterviewAiFeedbackRequest(
                request.answers().stream()
                        .map(answer -> new InterviewAiFeedbackRequest.Answer(
                                answer.id(), answer.question(), answer.intent(), answer.keyPoints(), answer.answer()))
                        .toList()));
        Map<String, InterviewAiFeedbackResponse.Feedback> feedbackById = validateFeedback(request, generated);

        List<InterviewAnswer> answers = request.answers().stream()
                .map(answer -> {
                    InterviewAiFeedbackResponse.Feedback feedback = feedbackById.get(answer.id());
                    return new InterviewAnswer(answer.id(), answer.question(), answer.intent(), answer.keyPoints(),
                            answer.answer(), feedback.feedback(), feedback.score());
                })
                .toList();
        return interviewSessionStore.create(userId, answers);
    }

    public InterviewSessionListResponse getSessions(Long userId, int page, int size) {
        return interviewSessionStore.findSessions(userId, page, size);
    }

    public InterviewSessionResponse getSession(Long userId, Long id) {
        return interviewSessionStore.findSession(userId, id);
    }

    /** AI 서버가 약속한 모양을 지키지 않았다. 사용자의 요청 탓이 아니므로 잠시 쓸 수 없다고 답한다. */
    private Map<String, InterviewAiFeedbackResponse.Feedback> validateFeedback(
            SubmitInterviewAnswersRequest request, InterviewAiFeedbackResponse response) {
        if (response == null || response.feedbacks() == null
                || response.feedbacks().size() != request.answers().size()) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE);
        }
        boolean invalid = response.feedbacks().stream().anyMatch(feedback ->
                feedback == null || isBlank(feedback.id()) || isBlank(feedback.feedback())
                        || feedback.score() < 0 || feedback.score() > 100);
        if (invalid) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE);
        }
        Map<String, InterviewAiFeedbackResponse.Feedback> feedbackById = response.feedbacks().stream()
                .collect(Collectors.toMap(InterviewAiFeedbackResponse.Feedback::id, feedback -> feedback));
        Set<String> requestedIds = request.answers().stream()
                .map(SubmitInterviewAnswersRequest.Answer::id)
                .collect(Collectors.toSet());
        if (!feedbackById.keySet().equals(requestedIds)) {
            throw new GeneralException(GeneralErrorCode.SERVICE_UNAVAILABLE);
        }
        return feedbackById;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
