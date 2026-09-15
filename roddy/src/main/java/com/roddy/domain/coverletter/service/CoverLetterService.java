package com.roddy.domain.coverletter.service;

import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.coverletter.dto.*;
import com.roddy.domain.coverletter.entity.*;
import com.roddy.domain.coverletter.repository.CoverLetterRepository;
import com.roddy.domain.jobposting.entity.JobPosting;
import com.roddy.domain.jobposting.repository.JobPostingRepository;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoverLetterService {
    private final CoverLetterRepository repository;
    private final UserRepository userRepository;
    private final JobPostingRepository jobPostingRepository;

    public CoverLetterListResponse list(Long userId, int page, int size) {
        return CoverLetterListResponse.from(repository.findAllByUserId(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt", "id"))));
    }

    public CoverLetterResponse get(Long userId, Long id) {
        return CoverLetterResponse.from(findOwned(userId, id));
    }

    @Transactional
    public CoverLetterResponse create(Long userId, SaveCoverLetterRequest request) {
        var user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
        var letter = CoverLetter.create(user, request.title(), findJob(request.jobPostingId()), answers(request));
        return CoverLetterResponse.from(repository.saveAndFlush(letter));
    }

    @Transactional
    public CoverLetterResponse update(Long userId, Long id, SaveCoverLetterRequest request) {
        var letter = findOwned(userId, id);
        if (request.version() == null) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "수정할 문서의 version이 필요합니다.");
        }
        requireVersion(letter, request.version());
        letter.replace(request.title(), findJob(request.jobPostingId()), answers(request));
        try {
            repository.flush();
        } catch (OptimisticLockingFailureException exception) {
            throw new GeneralException(GeneralErrorCode.COVER_LETTER_CONFLICT);
        }
        return CoverLetterResponse.from(letter);
    }

    @Transactional
    public void delete(Long userId, Long id, long version) {
        var letter = findOwned(userId, id);
        requireVersion(letter, version);
        try {
            repository.delete(letter);
            repository.flush();
        } catch (OptimisticLockingFailureException exception) {
            throw new GeneralException(GeneralErrorCode.COVER_LETTER_CONFLICT);
        }
    }

    private void requireVersion(CoverLetter letter, long version) {
        if (letter.getVersion() != version) {
            throw new GeneralException(GeneralErrorCode.COVER_LETTER_CONFLICT);
        }
    }

    /** 타인 문서도 없는 문서와 같은 응답으로 처리한다. */
    private CoverLetter findOwned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.COVER_LETTER_NOT_FOUND));
    }

    private JobPosting findJob(Long id) {
        return id == null ? null : jobPostingRepository.findById(id)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.JOB_POSTING_NOT_FOUND));
    }

    private java.util.List<CoverLetterAnswer> answers(SaveCoverLetterRequest request) {
        return request.answers().stream().map(a -> new CoverLetterAnswer(a.question(), a.answer())).toList();
    }
}
