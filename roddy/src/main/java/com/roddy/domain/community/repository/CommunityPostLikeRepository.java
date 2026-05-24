package com.roddy.domain.community.repository;

import com.roddy.domain.community.entity.CommunityPostLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommunityPostLikeRepository extends JpaRepository<CommunityPostLike, Long> {

    boolean existsByPost_IdAndUser_Id(Long postId, Long userId);

    Optional<CommunityPostLike> findByPost_IdAndUser_Id(Long postId, Long userId);
}
