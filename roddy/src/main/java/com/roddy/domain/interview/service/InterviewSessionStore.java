package com.roddy.domain.interview.service;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.interview.dto.InterviewSessionListResponse;
import com.roddy.domain.interview.dto.InterviewSessionResponse;
import com.roddy.domain.interview.entity.InterviewAnswer;
import com.roddy.domain.interview.entity.InterviewSession;
import com.roddy.domain.interview.repository.InterviewSessionRepository;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 모의면접 회차의 조회와 저장. 트랜잭션은 여기서만 연다.
 *
 * <p>{@link InterviewService} 가 AI 호출을 트랜잭션 밖에서 다룰 수 있도록 나눠 두었다.
 */
@Service
@RequiredArgsConstructor
public class InterviewSessionStore {

    private final UserRepository userRepository;
    private final InterviewSessionRepository interviewSessionRepository;

    @Transactional
    public InterviewSessionResponse create(Long userId, List<InterviewAnswer> answers) {
        InterviewSession session = InterviewSession.create(requireUser(userId), answers);
        return InterviewSessionResponse.from(interviewSessionRepository.saveAndFlush(session));
    }

    @Transactional(readOnly = true)
    public InterviewSessionListResponse findSessions(Long userId, int page, int size) {
        return InterviewSessionListResponse.from(interviewSessionRepository.findAllByUserId(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"))));
    }

    @Transactional(readOnly = true)
    public InterviewSessionResponse findSession(Long userId, Long id) {
        return InterviewSessionResponse.from(interviewSessionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INTERVIEW_SESSION_NOT_FOUND)));
    }

    private User requireUser(Long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }
}
