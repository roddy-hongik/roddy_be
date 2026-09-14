package com.roddy.domain.community.repository;

import com.roddy.domain.community.entity.CommunityCommentReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommunityCommentReportRepository extends JpaRepository<CommunityCommentReport, Long> {

    boolean existsByComment_IdAndUser_Id(Long commentId, Long userId);

    /** 글쓴이별로 그 사람 댓글에 들어온 신고 수. */
    @Query("""
            select c.author.id as userId, count(r) as reportCount
            from CommunityCommentReport r join r.comment c
            where c.author.id in :userIds
            group by c.author.id
            """)
    List<CommunityPostReportRepository.UserReportCount> countByAuthorIds(@Param("userIds") Collection<Long> userIds);

    /** 신고가 들어온 댓글과 신고 수. */
    @Query("""
            select r.comment.id as commentId, count(r) as reportCount
            from CommunityCommentReport r
            group by r.comment.id
            """)
    List<CommentReportCount> countByComment();

    /** 글에 달린 모든 댓글의 신고. 글을 지우기 전에 먼저 지운다. */
    @Modifying
    @Query("delete from CommunityCommentReport r where r.comment.id in (select c.id from CommunityComment c where c.post.id = :postId)")
    void deleteAllByPostId(@Param("postId") Long postId);

    /** 댓글과 그 대댓글에 들어온 신고. */
    @Modifying
    @Query("""
            delete from CommunityCommentReport r
            where r.comment.id = :commentId
               or r.comment.id in (select c.id from CommunityComment c where c.parentComment.id = :commentId)
            """)
    void deleteAllByCommentIdWithReplies(@Param("commentId") Long commentId);

    interface CommentReportCount {
        Long getCommentId();

        long getReportCount();
    }
}
