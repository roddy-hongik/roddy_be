package com.roddy.domain.admin.dto;

import com.roddy.domain.community.entity.CommunityComment;
import com.roddy.domain.community.entity.CommunityPost;

import java.time.LocalDateTime;

/**
 * 신고가 들어온 글이나 댓글 하나.
 *
 * @param id             글 id 또는 댓글 id. 글과 댓글의 id 는 서로 겹칠 수 있어 type 과 함께 가리킨다
 * @param contentPreview 목록에 보일 요약. 글은 제목, 댓글은 앞부분이다
 * @param status         목록에는 지워지지 않은 콘텐츠만 오므로 늘 reported 다
 */
public record ReportedContentResponse(
        Long id,
        ReportedContentType type,
        String author,
        String contentPreview,
        String fullContent,
        long reportCount,
        LocalDateTime createdAt,
        String status
) {

    private static final int PREVIEW_LENGTH = 80;
    private static final String REPORTED = "reported";

    public static ReportedContentResponse of(CommunityPost post) {
        return new ReportedContentResponse(
                post.getId(),
                ReportedContentType.POST,
                post.getAuthor().getNickname(),
                post.getTitle(),
                post.getContent(),
                post.getReportCount(),
                post.getCreatedAt(),
                REPORTED);
    }

    public static ReportedContentResponse of(CommunityComment comment, long reportCount) {
        String content = comment.getContent();
        return new ReportedContentResponse(
                comment.getId(),
                ReportedContentType.COMMENT,
                comment.getAuthor().getNickname(),
                content.length() <= PREVIEW_LENGTH ? content : content.substring(0, PREVIEW_LENGTH) + "...",
                content,
                reportCount,
                comment.getCreatedAt(),
                REPORTED);
    }
}
