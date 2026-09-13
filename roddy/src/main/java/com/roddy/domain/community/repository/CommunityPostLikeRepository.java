package com.roddy.domain.community.repository;

import com.roddy.domain.community.entity.CommunityPostLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLike, Long> {

    boolean existsByPost_IdAndUser_Id(Long postId, Long userId);

    Optional<CommunityPostLike> findByPost_IdAndUser_Id(Long postId, Long userId);

    @Modifying
    @Query("delete from CommunityPostLike l where l.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);

    /** 내가 좋아요한 글의 id. 좋아요를 누른 최신순이다. 글 본문은 id 로 따로 한 번에 읽는다. */
    @Query(
            value = """
                    select l.post.id from CommunityPostLike l
                    where l.user.id = :userId
                    order by l.createdAt desc, l.id desc
                    """,
            countQuery = """
                    select count(l) from CommunityPostLike l
                    where l.user.id = :userId
                    """
    )
    Page<Long> findLikedPostIds(@Param("userId") Long userId, Pageable pageable);
}
