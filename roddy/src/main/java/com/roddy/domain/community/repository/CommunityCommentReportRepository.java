package com.roddy.domain.community.repository;

import com.roddy.domain.community.entity.CommunityCommentReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommunityCommentReportRepository extends JpaRepository<CommunityCommentReport, Long> {

    boolean existsByComment_IdAndUser_Id(Long commentId, Long userId);
}
