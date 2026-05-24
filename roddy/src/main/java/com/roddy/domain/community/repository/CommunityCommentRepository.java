package com.roddy.domain.community.repository;

import com.roddy.domain.community.entity.CommunityComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {

    @Query("""
            select c
            from CommunityComment c
            join fetch c.author
            where c.post.id = :postId
            order by c.createdAt asc
            """)
    List<CommunityComment> findAllByPostIdOrderByCreatedAtAsc(Long postId);
}
