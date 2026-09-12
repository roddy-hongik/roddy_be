package com.roddy.domain.jobposting.repository;

import com.roddy.domain.jobposting.entity.JobBookmark;
import com.roddy.domain.jobposting.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface JobBookmarkRepository extends JpaRepository<JobBookmark, Long> {

    Optional<JobBookmark> findByUserIdAndJobPostingId(Long userId, Long jobPostingId);

    boolean existsByUserIdAndJobPostingId(Long userId, Long jobPostingId);

    List<JobBookmark> findAllByUserId(Long userId);

    /** 목록 한 페이지 안에서 스크랩한 공고만 한 번에 찾는다. */
    @Query("""
            select b.jobPosting.id from JobBookmark b
            where b.user.id = :userId and b.jobPosting.id in :jobPostingIds
            """)
    List<Long> findScrappedJobPostingIds(@Param("userId") Long userId,
                                         @Param("jobPostingIds") Collection<Long> jobPostingIds);

    /** 내 스크랩 목록. 최근에 담은 순. */
    @Query("""
            select b.jobPosting from JobBookmark b
            where b.user.id = :userId
            order by b.createdAt desc
            """)
    List<JobPosting> findScrappedJobPostings(@Param("userId") Long userId);
}
