package com.roddy.domain.community.service;

import com.roddy.domain.community.entity.CommunityComment;
import com.roddy.domain.community.entity.CommunityPost;
import com.roddy.domain.community.repository.CommunityCommentReportRepository;
import com.roddy.domain.community.repository.CommunityCommentRepository;
import com.roddy.domain.community.repository.CommunityPostLikeRepository;
import com.roddy.domain.community.repository.CommunityPostReportRepository;
import com.roddy.domain.community.repository.CommunityPostRepository;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import com.roddy.global.config.s3.S3Uploader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * 커뮤니티 글과 댓글을 딸린 데이터와 함께 지운다.
 *
 * <p>신고·좋아요·댓글은 글을 외래 키로 가리키기만 하고 엔티티로 묶여 있지 않아서 글보다 먼저 지워야 한다.
 * 이미지와 로드맵·인터뷰 상세, 태그는 글에 묶여 있어 글과 함께 지워진다.
 */
@Component
@RequiredArgsConstructor
public class CommunityContentRemover {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityPostReportRepository communityPostReportRepository;
    private final CommunityCommentReportRepository communityCommentReportRepository;
    private final CommunityPostLikeRepository communityPostLikeRepository;
    private final S3Uploader s3Uploader;

    @Transactional
    public void removePost(Long postId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.COMMUNITY_POST_NOT_FOUND));
        List<String> imageUrls = post.getImages().stream()
                .map(image -> image.getImageUrl())
                .distinct()
                .toList();

        communityCommentReportRepository.deleteAllByPostId(postId);
        communityCommentRepository.deleteRepliesByPostId(postId);
        communityCommentRepository.deleteAllByPostId(postId);
        communityPostReportRepository.deleteAllByPostId(postId);
        communityPostLikeRepository.deleteAllByPostId(postId);
        communityPostRepository.delete(post);
        deleteImagesAfterCommit(imageUrls);
    }

    /** 대댓글도 함께 지운다. 대댓글은 댓글에 묶여 있어 댓글을 지우면 따라 지워진다. */
    @Transactional
    public void removeComment(Long commentId) {
        CommunityComment comment = communityCommentRepository.findById(commentId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.COMMUNITY_COMMENT_NOT_FOUND));

        communityCommentReportRepository.deleteAllByCommentIdWithReplies(commentId);
        communityCommentRepository.delete(comment);
    }

    /** DB 삭제가 확정된 뒤에만 외부 저장소 파일을 지운다. */
    private void deleteImagesAfterCommit(List<String> imageUrls) {
        if (imageUrls.isEmpty()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                imageUrls.forEach(s3Uploader::deleteFile);
            }
        });
    }
}
