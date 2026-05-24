package com.roddy.domain.community.repository;

import com.roddy.domain.community.entity.CommunityPost;
import com.roddy.domain.community.enums.CommunityTag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {

    @EntityGraph(attributePaths = "author")
    Page<CommunityPost> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @EntityGraph(attributePaths = "author")
    Page<CommunityPost> findByTagOrderByCreatedAtDesc(CommunityTag tag, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "images"})
    Optional<CommunityPost> findWithAuthorAndImagesById(Long id);
}
