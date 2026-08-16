package com.roddy.domain.community.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.community.dto.request.CommunityPostSearchCondition;
import com.roddy.domain.community.dto.request.CreateCommunityCommentRequest;
import com.roddy.domain.community.dto.request.CreateCommunityPostRequest;
import com.roddy.domain.community.dto.response.CommunityCommentResponse;
import com.roddy.domain.community.dto.response.CommunityPostDetailResponse;
import com.roddy.domain.community.dto.response.CommunityPostListItemResponse;
import com.roddy.domain.community.dto.response.CommunityPostListResponse;
import com.roddy.domain.community.dto.response.CommunityRoadmapStepResponse;
import com.roddy.domain.community.dto.response.CreateCommunityPostResponse;
import com.roddy.domain.community.dto.response.ReportPostResponse;
import com.roddy.domain.community.dto.response.TogglePostLikeResponse;
import com.roddy.domain.community.entity.CommunityComment;
import com.roddy.domain.community.entity.CommunityCommentReport;
import com.roddy.domain.community.entity.CommunityInterviewPostDetail;
import com.roddy.domain.community.entity.CommunityPost;
import com.roddy.domain.community.entity.CommunityPostImage;
import com.roddy.domain.community.entity.CommunityPostLike;
import com.roddy.domain.community.entity.CommunityPostReport;
import com.roddy.domain.community.entity.CommunityRoadmapPostDetail;
import com.roddy.domain.community.entity.CommunityRoadmapPostStep;
import com.roddy.domain.community.repository.CommunityCommentRepository;
import com.roddy.domain.community.repository.CommunityCommentReportRepository;
import com.roddy.domain.community.repository.CommunityPostLikeRepository;
import com.roddy.domain.community.repository.CommunityPostReportRepository;
import com.roddy.domain.community.repository.CommunityPostRepository;
import com.roddy.domain.community.enums.CommunityInterviewSubtype;
import com.roddy.domain.community.enums.CommunityPostCategory;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import com.roddy.global.config.s3.S3Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommunityPostService {

    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of("image/png", "image/jpeg");
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg");

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityCommentReportRepository communityCommentReportRepository;
    private final CommunityPostLikeRepository communityPostLikeRepository;
    private final CommunityPostReportRepository communityPostReportRepository;
    private final UserRepository userRepository;
    private final S3Uploader s3Uploader;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public CommunityPostListResponse getPosts(CommunityPostSearchCondition condition, int page, int size, String sort) {
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Page<CommunityPost> posts = communityPostRepository.search(condition, pageable);

        return new CommunityPostListResponse(
                posts.stream()
                        .map(this::toListItemResponse)
                        .toList(),
                posts.getNumber(),
                posts.getSize(),
                posts.getTotalElements(),
                posts.getTotalPages()
        );
    }

    @Transactional
    public CommunityPostDetailResponse getPost(Long postId, Long currentUserId) {
        CommunityPost post = getPostOrThrow(postId);
        post.increaseViewCount();

        List<CommunityCommentResponse> comments = getComments(postId);

        boolean liked = currentUserId != null && communityPostLikeRepository.existsByPost_IdAndUser_Id(postId, currentUserId);

        return new CommunityPostDetailResponse(
                post.getId(),
                toPostType(post),
                toTagKey(post),
                buildTags(post),
                post.getTitle(),
                post.getContent(),
                post.getAuthor().getNickname(),
                post.getCreatedAt(),
                post.getViewCount(),
                post.getLikeCount(),
                getCommentCount(post.getId()),
                liked,
                createExcerpt(post.getContent()),
                extractRoadmapId(post),
                extractRoadmapTitle(post),
                extractRoadmapSummary(post),
                extractRoadmapTargetJob(post),
                extractRoadmapTargetCompany(post),
                extractRecommendedSkills(post),
                extractRoadmapSteps(post),
                extractRoadmapDescription(post),
                extractInterviewSubtype(post),
                extractCompany(post),
                extractJobRole(post),
                extractPreparationPeriod(post),
                extractTechStacks(post),
                extractProcessSummary(post),
                extractBackground(post),
                extractPreparationProcess(post),
                extractExperienceDetail(post),
                extractAdvice(post),
                post.getImages().stream().map(CommunityPostImage::getImageUrl).toList(),
                comments
        );
    }

    @Transactional
    public CreateCommunityPostResponse createPost(Long userId, CreateCommunityPostRequest request) {
        User author = getUserOrThrow(userId);
        CommunityPost post = CommunityPost.create(
                author,
                request.getPostCategory(),
                request.getJobCategory(),
                request.getTitle().trim(),
                request.getContent().trim(),
                request.getTags()
        );
        attachTypedDetail(post, request);

        List<String> uploadedImageUrls = new ArrayList<>();
        try {
            for (MultipartFile image : safeImages(request.getImages())) {
                validateImage(image);
                String imageUrl = s3Uploader.upload(image, "community/posts/" + userId);
                uploadedImageUrls.add(imageUrl);
                post.addImage(CommunityPostImage.create(post, imageUrl, image.getOriginalFilename()));
            }
        } catch (IOException exception) {
            rollbackUploadedImages(uploadedImageUrls);
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR, "이미지 업로드에 실패했습니다.");
        } catch (RuntimeException exception) {
            rollbackUploadedImages(uploadedImageUrls);
            throw exception;
        }

        try {
            CommunityPost savedPost = communityPostRepository.save(post);
            return new CreateCommunityPostResponse(savedPost.getId());
        } catch (RuntimeException exception) {
            rollbackUploadedImages(uploadedImageUrls);
            throw exception;
        }
    }

    @Transactional
    public TogglePostLikeResponse toggleLike(Long postId, Long userId) {
        CommunityPost post = getPostOrThrow(postId);
        User user = getUserOrThrow(userId);

        return communityPostLikeRepository.findByPost_IdAndUser_Id(postId, userId)
                .map(existingLike -> {
                    communityPostLikeRepository.delete(existingLike);
                    post.decreaseLikeCount();
                    return new TogglePostLikeResponse(false, post.getLikeCount());
                })
                .orElseGet(() -> {
                    communityPostLikeRepository.save(CommunityPostLike.create(post, user));
                    post.increaseLikeCount();
                    return new TogglePostLikeResponse(true, post.getLikeCount());
                });
    }

    @Transactional
    public ReportPostResponse reportPost(Long postId, Long userId) {
        CommunityPost post = getPostOrThrow(postId);
        User user = getUserOrThrow(userId);

        if (communityPostReportRepository.existsByPost_IdAndUser_Id(postId, userId)) {
            throw new GeneralException(GeneralErrorCode.COMMUNITY_POST_ALREADY_REPORTED);
        }

        try {
            communityPostReportRepository.save(CommunityPostReport.create(post, user, null));
            post.increaseReportCount();
        } catch (DataIntegrityViolationException exception) {
            throw new GeneralException(GeneralErrorCode.COMMUNITY_POST_ALREADY_REPORTED);
        }

        return new ReportPostResponse(true);
    }

    @Transactional
    public CommunityCommentResponse createComment(Long postId, Long userId, CreateCommunityCommentRequest request) {
        CommunityPost post = getPostOrThrow(postId);
        User author = getUserOrThrow(userId);
        CommunityComment parentComment = resolveParentComment(postId, request.parentCommentId());

        CommunityComment comment = communityCommentRepository.save(
                CommunityComment.create(post, author, request.content().trim(), parentComment)
        );

        return toCommentResponse(comment);
    }

    @Transactional(readOnly = true)
    public List<CommunityCommentResponse> getComments(Long postId) {
        getPostOrThrow(postId);
        return communityCommentRepository.findAllByPostIdOrderByThread(postId)
                .stream()
                .map(this::toCommentResponse)
                .toList();
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        CommunityComment comment = getCommentOrThrow(commentId);
        if (!comment.isAuthor(userId)) {
            throw new GeneralException(GeneralErrorCode.COMMUNITY_COMMENT_DELETE_FORBIDDEN);
        }
        communityCommentRepository.delete(comment);
    }

    @Transactional
    public ReportPostResponse reportComment(Long commentId, Long userId) {
        CommunityComment comment = getCommentOrThrow(commentId);
        User user = getUserOrThrow(userId);

        if (communityCommentReportRepository.existsByComment_IdAndUser_Id(commentId, userId)) {
            throw new GeneralException(GeneralErrorCode.COMMUNITY_COMMENT_ALREADY_REPORTED);
        }

        try {
            communityCommentReportRepository.save(CommunityCommentReport.create(comment, user, null));
        } catch (DataIntegrityViolationException exception) {
            throw new GeneralException(GeneralErrorCode.COMMUNITY_COMMENT_ALREADY_REPORTED);
        }

        return new ReportPostResponse(true);
    }

    private CommunityPost getPostOrThrow(Long postId) {
        return communityPostRepository.findDetailById(postId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.COMMUNITY_POST_NOT_FOUND));
    }

    private CommunityComment getCommentOrThrow(Long commentId) {
        return communityCommentRepository.findById(commentId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.COMMUNITY_COMMENT_NOT_FOUND));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }

    private CommunityPostListItemResponse toListItemResponse(CommunityPost post) {
        return new CommunityPostListItemResponse(
                post.getId(),
                toPostType(post),
                toTagKey(post),
                buildTags(post),
                post.getTitle(),
                post.getAuthor().getNickname(),
                post.getCreatedAt(),
                post.getViewCount(),
                post.getLikeCount(),
                getCommentCount(post.getId()),
                createExcerpt(post.getContent()),
                post.getContent(),
                extractRoadmapId(post),
                extractRoadmapTitle(post),
                extractRoadmapSummary(post),
                extractRoadmapTargetJob(post),
                extractRoadmapTargetCompany(post),
                extractRecommendedSkills(post),
                extractRoadmapSteps(post),
                extractRoadmapDescription(post),
                extractInterviewSubtype(post),
                extractCompany(post),
                extractJobRole(post),
                extractPreparationPeriod(post),
                extractTechStacks(post),
                extractProcessSummary(post),
                extractBackground(post),
                extractPreparationProcess(post),
                extractExperienceDetail(post),
                extractAdvice(post)
        );
    }

    private CommunityCommentResponse toCommentResponse(CommunityComment comment) {
        return new CommunityCommentResponse(
                comment.getId(),
                comment.getAuthor().getNickname(),
                comment.getContent(),
                comment.getParentComment() == null ? null : comment.getParentComment().getId(),
                comment.getDepth(),
                comment.getCreatedAt()
        );
    }

    private void attachTypedDetail(CommunityPost post, CreateCommunityPostRequest request) {
        if (post.getPostCategory() == CommunityPostCategory.ROADMAP) {
            validateRoadmapRequest(request);
            CommunityRoadmapPostDetail roadmapDetail = CommunityRoadmapPostDetail.create(
                    post,
                    request.getRoadmapId(),
                    defaultIfBlank(request.getRoadmapTitle(), post.getTitle()),
                    defaultIfBlank(request.getSummary(), post.getContent()),
                    defaultIfBlank(request.getDescription(), post.getContent()),
                    defaultIfBlank(request.getTargetJob(), resolveJobRole(request, post.getJobCategory().getDisplayName())),
                    defaultIfBlank(request.getTargetCompany(), request.getCompany()),
                    firstNonEmpty(request.getRecommendedSkills(), request.getTechStacks())
            );
            roadmapDetail.replaceSteps(parseRoadmapSteps(roadmapDetail, request.getRoadmapStepsJson()));
            post.attachRoadmapDetail(roadmapDetail);
            return;
        }

        if (post.getPostCategory() == CommunityPostCategory.PASS_REVIEW_INTERVIEW) {
            validateInterviewRequest(request);
            post.attachInterviewDetail(CommunityInterviewPostDetail.create(
                    post,
                    request.getInterviewSubtype() == null ? CommunityInterviewSubtype.ACCEPTED : request.getInterviewSubtype(),
                    defaultIfBlank(request.getCompany(), "미입력"),
                    resolveJobRole(request, post.getJobCategory().getDisplayName()),
                    defaultIfBlank(request.getPreparationPeriod(), "미입력"),
                    request.getTechStacks(),
                    defaultIfBlank(request.getProcessSummary(), post.getContent()),
                    defaultIfBlank(request.getBackground(), ""),
                    defaultIfBlank(request.getPreparationProcess(), ""),
                    defaultIfBlank(request.getExperienceDetail(), ""),
                    defaultIfBlank(request.getAdvice(), "")
            ));
        }
    }

    private CommunityComment resolveParentComment(Long postId, Long parentCommentId) {
        if (parentCommentId == null) {
            return null;
        }

        CommunityComment parentComment = communityCommentRepository.findByIdAndPost_Id(parentCommentId, postId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.COMMUNITY_COMMENT_NOT_FOUND));

        if (parentComment.isReply()) {
            throw new GeneralException(GeneralErrorCode.COMMUNITY_COMMENT_PARENT_INVALID);
        }

        return parentComment;
    }

    private String extractCompany(CommunityPost post) {
        if (post.getRoadmapDetail() != null) {
            return post.getRoadmapDetail().getTargetCompany();
        }
        if (post.getInterviewDetail() != null) {
            return post.getInterviewDetail().getCompany();
        }
        return null;
    }

    private String extractJobRole(CommunityPost post) {
        if (post.getRoadmapDetail() != null) {
            return post.getRoadmapDetail().getTargetJob();
        }
        if (post.getInterviewDetail() != null) {
            return post.getInterviewDetail().getJobRole();
        }
        return null;
    }

    private String extractPreparationPeriod(CommunityPost post) {
        return post.getInterviewDetail() == null ? null : post.getInterviewDetail().getPreparationPeriod();
    }

    private List<String> extractTechStacks(CommunityPost post) {
        if (post.getRoadmapDetail() != null) {
            return new ArrayList<>(post.getRoadmapDetail().getRecommendedSkills());
        }
        if (post.getInterviewDetail() != null) {
            return new ArrayList<>(post.getInterviewDetail().getTechStacks());
        }
        return List.of();
    }

    private String extractProcessSummary(CommunityPost post) {
        return post.getInterviewDetail() == null ? null : post.getInterviewDetail().getProcessSummary();
    }

    private String extractBackground(CommunityPost post) {
        return post.getInterviewDetail() == null ? null : post.getInterviewDetail().getBackground();
    }

    private String extractPreparationProcess(CommunityPost post) {
        return post.getInterviewDetail() == null ? null : post.getInterviewDetail().getPreparationProcess();
    }

    private String extractExperienceDetail(CommunityPost post) {
        return post.getInterviewDetail() == null ? null : post.getInterviewDetail().getExperienceDetail();
    }

    private String extractAdvice(CommunityPost post) {
        return post.getInterviewDetail() == null ? null : post.getInterviewDetail().getAdvice();
    }

    private String extractRoadmapId(CommunityPost post) {
        return post.getRoadmapDetail() == null ? null : post.getRoadmapDetail().getRoadmapId();
    }

    private String extractRoadmapTitle(CommunityPost post) {
        return post.getRoadmapDetail() == null ? null : post.getRoadmapDetail().getRoadmapTitle();
    }

    private String extractRoadmapSummary(CommunityPost post) {
        return post.getRoadmapDetail() == null ? null : post.getRoadmapDetail().getSummary();
    }

    private String extractRoadmapTargetJob(CommunityPost post) {
        return post.getRoadmapDetail() == null ? null : post.getRoadmapDetail().getTargetJob();
    }

    private String extractRoadmapTargetCompany(CommunityPost post) {
        return post.getRoadmapDetail() == null ? null : post.getRoadmapDetail().getTargetCompany();
    }

    private List<String> extractRecommendedSkills(CommunityPost post) {
        return post.getRoadmapDetail() == null ? List.of() : new ArrayList<>(post.getRoadmapDetail().getRecommendedSkills());
    }

    private List<CommunityRoadmapStepResponse> extractRoadmapSteps(CommunityPost post) {
        if (post.getRoadmapDetail() == null) {
            return List.of();
        }

        return post.getRoadmapDetail().getRoadmapSteps().stream()
                .map(step -> new CommunityRoadmapStepResponse(
                        step.getStage(),
                        step.getGoal(),
                        new ArrayList<>(step.getTopics()),
                        new ArrayList<>(step.getOutputs())
                ))
                .toList();
    }

    private String extractRoadmapDescription(CommunityPost post) {
        return post.getRoadmapDetail() == null ? null : post.getRoadmapDetail().getDescription();
    }

    private String extractInterviewSubtype(CommunityPost post) {
        if (post.getInterviewDetail() == null) {
            return null;
        }
        return post.getInterviewDetail().getSubtype() == CommunityInterviewSubtype.INCUMBENT ? "incumbent" : "accepted";
    }

    private String toPostType(CommunityPost post) {
        if (post.isRoadmapPost()) {
            return "roadmap";
        }
        if (post.isInterviewPost()) {
            return "interview";
        }
        return "general";
    }

    private String toTagKey(CommunityPost post) {
        return switch (post.getJobCategory()) {
            case B2C -> "b2c";
            case FINTECH -> "fintech";
            case B2B -> "b2b";
            case INFRA_DEVOPS -> "infra-devops";
            case GENERALIST -> "generalist";
        };
    }

    private List<String> buildTags(CommunityPost post) {
        Set<String> tags = new LinkedHashSet<>(post.getTags());

        if (post.isGeneralPost()) {
            tags.add(post.getPostCategory().getDisplayName());
        }

        if (post.getRoadmapDetail() != null) {
            tags.add(post.getRoadmapDetail().getRoadmapTitle());
            tags.add(post.getRoadmapDetail().getTargetJob());
            if (post.getRoadmapDetail().getTargetCompany() != null) {
                tags.add(post.getRoadmapDetail().getTargetCompany());
            }
            tags.addAll(post.getRoadmapDetail().getRecommendedSkills());
        }

        if (post.getInterviewDetail() != null) {
            tags.add(post.getInterviewDetail().getSubtype().getDisplayName());
            tags.add(post.getInterviewDetail().getCompany());
            tags.add(post.getInterviewDetail().getJobRole());
            tags.addAll(post.getInterviewDetail().getTechStacks());
        }

        return tags.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private String createExcerpt(String content) {
        if (!hasText(content)) {
            return "";
        }
        String trimmed = content.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }

    private int getCommentCount(Long postId) {
        return Math.toIntExact(communityCommentRepository.countByPost_Id(postId));
    }

    private String defaultIfBlank(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? defaultValue : trimmed;
    }

    private String resolveJobRole(CreateCommunityPostRequest request, String defaultValue) {
        return defaultIfBlank(request.getJobRole(), defaultIfBlank(request.getPosition(), defaultValue));
    }

    private List<String> firstNonEmpty(List<String> primary, List<String> fallback) {
        return primary != null && !primary.isEmpty() ? primary : fallback;
    }

    private void validateRoadmapRequest(CreateCommunityPostRequest request) {
        if (!hasText(request.getTargetJob()) && !hasText(request.getPosition()) && !hasText(request.getJobRole())) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "로드맵 글은 목표 직무가 필요합니다.");
        }
    }

    private void validateInterviewRequest(CreateCommunityPostRequest request) {
        if (!hasText(request.getCompany())) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "인터뷰 글은 회사명이 필요합니다.");
        }
        if (!hasText(request.getJobRole()) && !hasText(request.getPosition())) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "인터뷰 글은 직무가 필요합니다.");
        }
        if (!hasText(request.getPreparationPeriod())) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "인터뷰 글은 준비 기간이 필요합니다.");
        }
        if (!hasText(request.getProcessSummary())) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "인터뷰 글은 진행 요약이 필요합니다.");
        }
        if (!hasText(request.getBackground())) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "인터뷰 글은 배경 정보가 필요합니다.");
        }
        if (!hasText(request.getPreparationProcess())) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "인터뷰 글은 준비 과정이 필요합니다.");
        }
        if (!hasText(request.getExperienceDetail())) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "인터뷰 글은 상세 경험이 필요합니다.");
        }
        if (!hasText(request.getAdvice())) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "인터뷰 글은 조언 항목이 필요합니다.");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<CommunityRoadmapPostStep> parseRoadmapSteps(CommunityRoadmapPostDetail roadmapDetail, String roadmapStepsJson) {
        if (!hasText(roadmapStepsJson)) {
            return Collections.emptyList();
        }

        try {
            List<RoadmapStepPayload> stepPayloads = objectMapper.readValue(roadmapStepsJson, new TypeReference<>() {
            });
            List<CommunityRoadmapPostStep> steps = new ArrayList<>();
            for (int index = 0; index < stepPayloads.size(); index++) {
                RoadmapStepPayload payload = stepPayloads.get(index);
                steps.add(CommunityRoadmapPostStep.create(
                        roadmapDetail,
                        index,
                        defaultIfBlank(payload.stage(), ""),
                        defaultIfBlank(payload.goal(), ""),
                        payload.topics(),
                        payload.outputs()
                ));
            }
            return steps;
        } catch (JsonProcessingException exception) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "로드맵 단계 정보 형식이 올바르지 않습니다.");
        }
    }

    private record RoadmapStepPayload(
            String stage,
            String goal,
            List<String> topics,
            List<String> outputs
    ) {
    }

    private List<MultipartFile> safeImages(List<MultipartFile> images) {
        return images == null ? List.of() : images.stream().filter(file -> file != null && !file.isEmpty()).toList();
    }

    private void validateImage(MultipartFile image) {
        if (image.isEmpty()) {
            throw new GeneralException(GeneralErrorCode.INVALID_PARAMETER, "빈 파일은 업로드할 수 없습니다.");
        }

        String contentType = image.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new GeneralException(GeneralErrorCode.UNSUPPORTED_CONTENT_TYPE, "PNG, JPG, JPEG 파일만 업로드할 수 있습니다.");
        }

        String originalFilename = image.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new GeneralException(GeneralErrorCode.UNSUPPORTED_CONTENT_TYPE, "PNG, JPG, JPEG 파일만 업로드할 수 있습니다.");
        }
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return "";
        }
        return originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private void rollbackUploadedImages(List<String> uploadedImageUrls) {
        uploadedImageUrls.forEach(s3Uploader::deleteFile);
    }

    private Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank() || sort.equalsIgnoreCase("latest")) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        if (sort.equalsIgnoreCase("likeCount")) {
            return Sort.by(Sort.Order.desc("likeCount"), Sort.Order.desc("createdAt"));
        }
        if (sort.equalsIgnoreCase("viewCount")) {
            return Sort.by(Sort.Order.desc("viewCount"), Sort.Order.desc("createdAt"));
        }
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }
}
