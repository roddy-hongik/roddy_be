package com.roddy.domain.community.service;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.community.dto.request.CommunityPostSearchCondition;
import com.roddy.domain.community.dto.request.CreateCommunityCommentRequest;
import com.roddy.domain.community.dto.request.CreateCommunityPostRequest;
import com.roddy.domain.community.dto.response.CommunityCommentResponse;
import com.roddy.domain.community.dto.response.CommunityPostDetailResponse;
import com.roddy.domain.community.dto.response.CommunityPostListItemResponse;
import com.roddy.domain.community.dto.response.CommunityPostListResponse;
import com.roddy.domain.community.dto.response.CreateCommunityPostResponse;
import com.roddy.domain.community.dto.response.ReportPostResponse;
import com.roddy.domain.community.dto.response.TogglePostLikeResponse;
import com.roddy.domain.community.entity.CommunityComment;
import com.roddy.domain.community.entity.CommunityPost;
import com.roddy.domain.community.entity.CommunityPostImage;
import com.roddy.domain.community.entity.CommunityPostLike;
import com.roddy.domain.community.entity.CommunityPostReport;
import com.roddy.domain.community.repository.CommunityCommentRepository;
import com.roddy.domain.community.repository.CommunityPostLikeRepository;
import com.roddy.domain.community.repository.CommunityPostReportRepository;
import com.roddy.domain.community.repository.CommunityPostRepository;
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
import java.time.LocalDate;
import java.util.ArrayList;
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
    private final CommunityPostLikeRepository communityPostLikeRepository;
    private final CommunityPostReportRepository communityPostReportRepository;
    private final UserRepository userRepository;
    private final S3Uploader s3Uploader;

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

        List<CommunityCommentResponse> comments = communityCommentRepository.findAllByPostIdOrderByCreatedAtAsc(postId)
                .stream()
                .map(this::toCommentResponse)
                .toList();

        boolean liked = currentUserId != null && communityPostLikeRepository.existsByPost_IdAndUser_Id(postId, currentUserId);

        return new CommunityPostDetailResponse(
                post.getId(),
                post.getPostCategory().name(),
                post.getPostCategory().getDisplayName(),
                post.getJobCategory().name(),
                post.getJobCategory().getDisplayName(),
                post.getTitle(),
                post.getContent(),
                post.getAuthor().getNickname(),
                toLocalDate(post.getCreatedAt()),
                post.getViewCount(),
                post.getLikeCount(),
                liked,
                post.getCompany(),
                post.getPosition(),
                new ArrayList<>(post.getTechStacks()),
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
                request.getCompany(),
                request.getPosition(),
                request.getTechStacks()
        );

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

        CommunityComment comment = communityCommentRepository.save(
                CommunityComment.create(post, author, request.content().trim())
        );

        return toCommentResponse(comment);
    }

    private CommunityPost getPostOrThrow(Long postId) {
        return communityPostRepository.findDetailById(postId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.COMMUNITY_POST_NOT_FOUND));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }

    private CommunityPostListItemResponse toListItemResponse(CommunityPost post) {
        return new CommunityPostListItemResponse(
                post.getId(),
                post.getPostCategory().name(),
                post.getPostCategory().getDisplayName(),
                post.getJobCategory().name(),
                post.getJobCategory().getDisplayName(),
                post.getTitle(),
                post.getAuthor().getNickname(),
                toLocalDate(post.getCreatedAt()),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCompany(),
                post.getPosition(),
                new ArrayList<>(post.getTechStacks())
        );
    }

    private CommunityCommentResponse toCommentResponse(CommunityComment comment) {
        return new CommunityCommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getAuthor().getNickname(),
                toLocalDate(comment.getCreatedAt())
        );
    }

    private LocalDate toLocalDate(java.time.LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toLocalDate();
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
