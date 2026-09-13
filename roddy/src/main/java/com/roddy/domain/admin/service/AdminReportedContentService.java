package com.roddy.domain.admin.service;

import com.roddy.domain.admin.dto.ReportedContentResponse;
import com.roddy.domain.community.repository.CommunityCommentReportRepository;
import com.roddy.domain.community.repository.CommunityCommentReportRepository.CommentReportCount;
import com.roddy.domain.community.repository.CommunityCommentRepository;
import com.roddy.domain.community.repository.CommunityPostRepository;
import com.roddy.domain.community.service.CommunityContentRemover;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminReportedContentService {

    private final CommunityPostRepository communityPostRepository;
    private final CommunityCommentRepository communityCommentRepository;
    private final CommunityCommentReportRepository communityCommentReportRepository;
    private final CommunityContentRemover communityContentRemover;

    /** 신고가 들어온 글과 댓글. 신고 많은 순이고, 같으면 최근에 쓴 것이 앞이다. */
    @Transactional(readOnly = true)
    public List<ReportedContentResponse> getReportedContents() {
        Stream<ReportedContentResponse> posts = communityPostRepository.findAllReportedWithAuthor().stream()
                .map(ReportedContentResponse::of);

        Map<Long, Long> commentReportCounts = communityCommentReportRepository.countByComment().stream()
                .collect(Collectors.toMap(CommentReportCount::getCommentId, CommentReportCount::getReportCount));
        Stream<ReportedContentResponse> comments = commentReportCounts.isEmpty()
                ? Stream.empty()
                : communityCommentRepository.findAllWithAuthorByIdIn(commentReportCounts.keySet()).stream()
                        .map(comment -> ReportedContentResponse.of(comment, commentReportCounts.get(comment.getId())));

        return Stream.concat(posts, comments)
                .sorted(Comparator.comparingLong(ReportedContentResponse::reportCount).reversed()
                        .thenComparing(ReportedContentResponse::createdAt,
                                Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder())))
                .toList();
    }

    @Transactional
    public void removePost(Long postId, String reason) {
        communityContentRemover.removePost(postId);
        log.info("신고된 글을 지웠습니다. postId={} reason={}", postId, reason);
    }

    @Transactional
    public void removeComment(Long commentId, String reason) {
        communityContentRemover.removeComment(commentId);
        log.info("신고된 댓글을 지웠습니다. commentId={} reason={}", commentId, reason);
    }
}
