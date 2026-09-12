package com.roddy.domain.jobposting.repository;

import com.roddy.domain.jobposting.entity.RecommendJobPosting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecommendJobPostingRepository extends JpaRepository<RecommendJobPosting, Long> {

    List<RecommendJobPosting> findAllByUserIdOrderByMatchScoreDesc(Long userId);
}
