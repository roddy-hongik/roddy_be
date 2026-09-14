package com.roddy.domain.community.repository;

import com.roddy.domain.community.entity.CommunityPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long>, CommunityPostRepositoryCustom {

    /** 신고가 들어온 글. */
    @Query("select p from CommunityPost p join fetch p.author where p.reportCount > 0")
    List<CommunityPost> findAllReportedWithAuthor();

    @Query("select distinct d.targetCompany from CommunityRoadmapPostDetail d where d.targetCompany is not null")
    List<String> findRoadmapTargetCompanies();

    @Query("select distinct d.targetJob from CommunityRoadmapPostDetail d")
    List<String> findRoadmapTargetJobs();

    @Query("select distinct skill from CommunityRoadmapPostDetail d join d.recommendedSkills skill")
    List<String> findRoadmapRecommendedSkills();

    @Query("select distinct d.company from CommunityInterviewPostDetail d")
    List<String> findInterviewCompanies();

    @Query("select distinct d.jobRole from CommunityInterviewPostDetail d")
    List<String> findInterviewJobRoles();

    @Query("select distinct skill from CommunityInterviewPostDetail d join d.techStacks skill")
    List<String> findInterviewTechStacks();
}
