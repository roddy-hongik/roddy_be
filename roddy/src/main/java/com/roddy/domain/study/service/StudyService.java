package com.roddy.domain.study.service;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.study.dto.request.StudyPostCreateRequest;
import com.roddy.domain.study.dto.request.StudySearchCondition;
import com.roddy.domain.study.dto.response.MyStudyApplicationListResponse;
import com.roddy.domain.study.dto.response.MyStudyApplicationResponse;
import com.roddy.domain.study.dto.response.StudyApplicantSummaryResponse;
import com.roddy.domain.study.dto.response.StudyApplicationResponse;
import com.roddy.domain.study.dto.response.StudyCloseResponse;
import com.roddy.domain.study.dto.response.StudyPostCreateResponse;
import com.roddy.domain.study.dto.response.StudyPostDetailResponse;
import com.roddy.domain.study.dto.response.StudyPostListItemResponse;
import com.roddy.domain.study.dto.response.StudyPostListResponse;
import com.roddy.domain.study.entity.StudyApplication;
import com.roddy.domain.study.entity.StudyPost;
import com.roddy.domain.study.enums.StudyApplicationStatus;
import com.roddy.domain.study.enums.StudyMode;
import com.roddy.domain.study.enums.StudyRecruitStatus;
import com.roddy.domain.study.repository.StudyApplicationRepository;
import com.roddy.domain.study.repository.StudyPostRepository;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StudyService {

    private final StudyPostRepository studyPostRepository;
    private final StudyApplicationRepository studyApplicationRepository;
    private final UserRepository userRepository;

    @Transactional
    public StudyPostCreateResponse createStudyPost(Long userId, StudyPostCreateRequest request) {
        validateStudyRequest(request.mode(), request.location(), request.scheduledAt());

        User author = getUserOrThrow(userId);
        StudyPost studyPost = StudyPost.create(
                author,
                request.title(),
                request.content(),
                request.mode(),
                request.location(),
                request.scheduledAt(),
                request.capacity()
        );
        StudyPost savedStudyPost = studyPostRepository.save(studyPost);
        return new StudyPostCreateResponse(savedStudyPost.getId());
    }

    @Transactional(readOnly = true)
    public StudyPostListResponse getStudyPosts(StudySearchCondition condition, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StudyPost> studies = studyPostRepository.search(condition, pageable);

        return new StudyPostListResponse(
                studies.stream().map(this::toStudyPostListItem).toList(),
                studies.getNumber(),
                studies.getSize(),
                studies.getTotalElements(),
                studies.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public StudyPostDetailResponse getStudyPostDetail(Long studyId, Long currentUserId) {
        StudyPost studyPost = studyPostRepository.findDetailById(studyId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.STUDY_NOT_FOUND));
        boolean isAuthor = currentUserId != null && studyPost.isAuthor(currentUserId);

        StudyApplicationStatus myStatus = null;
        if (currentUserId != null) {
            myStatus = studyApplicationRepository.findByStudyPost_IdAndApplicant_Id(studyId, currentUserId)
                    .map(StudyApplication::getStatus)
                    .orElse(null);
        }

        List<StudyApplicantSummaryResponse> applicants = isAuthor
                ? studyApplicationRepository.findAllByStudyPostIdOrderByCreatedAtAsc(studyId).stream()
                .map(this::toStudyApplicantSummary)
                .toList()
                : List.of();

        return new StudyPostDetailResponse(
                studyPost.getId(),
                studyPost.getTitle(),
                studyPost.getContent(),
                studyPost.getAuthor().getNickname(),
                studyPost.getCreatedAt(),
                studyPost.getMode().name(),
                studyPost.getMode().getDisplayName(),
                studyPost.getLocation(),
                studyPost.getScheduledAt(),
                studyPost.getCapacity(),
                studyPost.getApplicantCount(),
                studyPost.getStatus().name(),
                studyPost.getStatus().getDisplayName(),
                myStatus == null ? null : myStatus.name(),
                myStatus == null ? null : myStatus.getDisplayName(),
                isAuthor,
                applicants
        );
    }

    @Transactional
    public StudyApplicationResponse applyToStudy(Long studyId, Long userId) {
        StudyPost studyPost = getStudyPostForUpdate(studyId);
        User applicant = getUserOrThrow(userId);

        if (studyPost.isAuthor(userId)) {
            throw new GeneralException(GeneralErrorCode.STUDY_AUTHOR_CANNOT_APPLY);
        }
        if (studyPost.isClosed()) {
            throw new GeneralException(GeneralErrorCode.STUDY_ALREADY_CLOSED);
        }
        if (studyPost.isFull()) {
            throw new GeneralException(GeneralErrorCode.STUDY_CAPACITY_FULL);
        }

        StudyApplication studyApplication = studyApplicationRepository
                .findByStudyPost_IdAndApplicant_Id(studyId, userId)
                .map(existing -> reapply(existing, studyPost))
                .orElseGet(() -> studyApplicationRepository.save(StudyApplication.create(studyPost, applicant)));

        studyPost.increaseApplicantCount();

        return new StudyApplicationResponse(
                studyApplication.getId(),
                studyApplication.getStatus().name(),
                studyApplication.getStatus().getDisplayName(),
                studyPost.getApplicantCount()
        );
    }

    @Transactional
    public StudyApplicationResponse cancelMyApplication(Long studyId, Long userId) {
        StudyPost studyPost = getStudyPostForUpdate(studyId);
        StudyApplication studyApplication = studyApplicationRepository.findByStudyPost_IdAndApplicant_Id(studyId, userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.STUDY_APPLICATION_NOT_FOUND));

        if (studyApplication.isCanceled()) {
            throw new GeneralException(GeneralErrorCode.STUDY_APPLICATION_ALREADY_CANCELED);
        }

        studyApplication.cancel();
        studyPost.decreaseApplicantCount();

        return new StudyApplicationResponse(
                studyApplication.getId(),
                studyApplication.getStatus().name(),
                studyApplication.getStatus().getDisplayName(),
                studyPost.getApplicantCount()
        );
    }

    @Transactional
    public StudyCloseResponse closeStudyPost(Long studyId, Long userId) {
        StudyPost studyPost = getStudyPostForUpdate(studyId);

        if (!studyPost.isAuthor(userId)) {
            throw new GeneralException(GeneralErrorCode.STUDY_FORBIDDEN);
        }
        if (!studyPost.isClosed()) {
            studyPost.close();
        }

        return new StudyCloseResponse(
                studyPost.getId(),
                studyPost.getStatus().name(),
                studyPost.getStatus().getDisplayName()
        );
    }

    @Transactional(readOnly = true)
    public MyStudyApplicationListResponse getMyApplications(Long userId, StudyApplicationStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<StudyApplication> applications = studyApplicationRepository.findMyApplications(userId, status, pageable);

        return new MyStudyApplicationListResponse(
                applications.stream().map(this::toMyApplicationResponse).toList(),
                applications.getNumber(),
                applications.getSize(),
                applications.getTotalElements(),
                applications.getTotalPages()
        );
    }

    private StudyApplication reapply(StudyApplication existing, StudyPost studyPost) {
        if (existing.isApplied()) {
            throw new GeneralException(GeneralErrorCode.STUDY_ALREADY_APPLIED);
        }
        existing.apply();
        return existing;
    }

    private StudyPostListItemResponse toStudyPostListItem(StudyPost post) {
        return new StudyPostListItemResponse(
                post.getId(),
                post.getTitle(),
                toContentPreview(post.getContent()),
                post.getMode().name(),
                post.getMode().getDisplayName(),
                post.getLocation(),
                post.getScheduledAt(),
                post.getCapacity(),
                post.getApplicantCount(),
                post.getStatus().name(),
                post.getStatus().getDisplayName()
        );
    }

    private MyStudyApplicationResponse toMyApplicationResponse(StudyApplication application) {
        StudyPost post = application.getStudyPost();
        return new MyStudyApplicationResponse(
                application.getId(),
                post.getId(),
                post.getTitle(),
                post.getMode().name(),
                post.getMode().getDisplayName(),
                post.getLocation(),
                post.getScheduledAt(),
                post.getCapacity(),
                post.getApplicantCount(),
                post.getStatus().name(),
                post.getStatus().getDisplayName(),
                application.getStatus().name(),
                application.getStatus().getDisplayName(),
                application.getCreatedAt()
        );
    }

    private StudyApplicantSummaryResponse toStudyApplicantSummary(StudyApplication application) {
        return new StudyApplicantSummaryResponse(
                application.getId(),
                application.getApplicant().getId(),
                application.getApplicant().getNickname(),
                application.getStatus().name(),
                application.getStatus().getDisplayName(),
                application.getCreatedAt()
        );
    }

    private StudyPost getStudyPostForUpdate(Long studyId) {
        return studyPostRepository.findByIdForUpdate(studyId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.STUDY_NOT_FOUND));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }

    private void validateStudyRequest(StudyMode mode, String location, LocalDateTime scheduledAt) {
        if (mode == StudyMode.OFFLINE && !StringUtils.hasText(location)) {
            throw new GeneralException(GeneralErrorCode.INVALID_STUDY_MODE_LOCATION);
        }
        if (scheduledAt.isBefore(LocalDateTime.now())) {
            throw new GeneralException(GeneralErrorCode.INVALID_STUDY_SCHEDULE);
        }
    }

    private String toContentPreview(String content) {
        String trimmed = content.trim();
        if (trimmed.length() <= 100) {
            return trimmed;
        }
        return trimmed.substring(0, 100);
    }
}
