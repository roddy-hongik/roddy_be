package com.roddy.domain.community.repository;

import com.roddy.domain.community.entity.CommunityPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long>, CommunityPostRepositoryCustom {
}
