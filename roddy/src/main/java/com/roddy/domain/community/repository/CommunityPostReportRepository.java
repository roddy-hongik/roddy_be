package com.roddy.domain.community.repository;

import com.roddy.domain.community.entity.CommunityPostReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityPostReportRepository extends JpaRepository<CommunityPostReport, Long> {

    boolean existsByPost_IdAndUser_Id(Long postId, Long userId);
}
