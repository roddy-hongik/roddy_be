package com.roddy.domain.community.repository;

import com.roddy.domain.community.entity.CommunityPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long>, CommunityPostRepositoryCustom {

    /** 신고가 들어온 글. */
    @Query("select p from CommunityPost p join fetch p.author where p.reportCount > 0")
    List<CommunityPost> findAllReportedWithAuthor();
}
