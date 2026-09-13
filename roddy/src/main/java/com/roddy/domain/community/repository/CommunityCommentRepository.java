package com.roddy.domain.community.repository;

import com.roddy.domain.community.entity.CommunityComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {

    @Query("""
            select c
            from CommunityComment c
            join fetch c.author
            where c.post.id = :postId
            order by
                case when c.parentComment is null then c.id else c.parentComment.id end asc,
                case when c.parentComment is null then 0 else 1 end asc,
                c.createdAt asc
            """)
    List<CommunityComment> findAllByPostIdOrderByThread(Long postId);

    Optional<CommunityComment> findByIdAndPost_Id(Long commentId, Long postId);

    long countByPost_Id(Long postId);

    @Query("select c from CommunityComment c join fetch c.author where c.id in :ids")
    List<CommunityComment> findAllWithAuthorByIdIn(@Param("ids") Collection<Long> ids);

    /** 대댓글부터 지운다. 부모 댓글을 먼저 지우면 부모를 가리키는 대댓글 때문에 외래 키에 걸린다. */
    @Modifying
    @Query("delete from CommunityComment c where c.post.id = :postId and c.parentComment is not null")
    void deleteRepliesByPostId(@Param("postId") Long postId);

    @Modifying
    @Query("delete from CommunityComment c where c.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);

    @Query("""
            select c.post.id as postId, count(c) as commentCount
            from CommunityComment c
            where c.post.id in :postIds
            group by c.post.id
            """)
    List<PostCommentCount> countByPostIds(@Param("postIds") List<Long> postIds);

    interface PostCommentCount {
        Long getPostId();

        long getCommentCount();
    }
}
