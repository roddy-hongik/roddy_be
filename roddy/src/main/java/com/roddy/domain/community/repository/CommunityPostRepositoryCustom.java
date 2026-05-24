package com.roddy.domain.community.repository;

import com.roddy.domain.community.dto.request.CommunityPostSearchCondition;
import com.roddy.domain.community.entity.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CommunityPostRepositoryCustom {

    Page<CommunityPost> search(CommunityPostSearchCondition condition, Pageable pageable);

    List<CommunityPost> findAllWithAuthorAndTechStacksByIds(List<Long> ids);

    Optional<CommunityPost> findDetailById(Long id);
}
