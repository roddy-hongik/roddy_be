package com.roddy.domain.community.repository;

import com.roddy.domain.community.entity.CommunityPostReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CommunityPostReportRepository extends JpaRepository<CommunityPostReport, Long> {

    boolean existsByPost_IdAndUser_Id(Long postId, Long userId);

    /** 글쓴이별로 그 사람 글에 들어온 신고 수. */
    @Query("""
            select p.author.id as userId, count(r) as reportCount
            from CommunityPostReport r join r.post p
            where p.author.id in :userIds
            group by p.author.id
            """)
    List<UserReportCount> countByAuthorIds(@Param("userIds") Collection<Long> userIds);

    @Modifying
    @Query("delete from CommunityPostReport r where r.post.id = :postId")
    void deleteAllByPostId(@Param("postId") Long postId);

    interface UserReportCount {
        Long getUserId();

        long getReportCount();
    }
}
