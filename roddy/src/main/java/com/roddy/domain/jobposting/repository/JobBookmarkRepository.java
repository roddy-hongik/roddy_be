package com.roddy.domain.jobposting.repository;

import com.roddy.domain.jobposting.entity.JobBookmark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobBookmarkRepository extends JpaRepository<JobBookmark, Long> {

    Optional<JobBookmark> findByUserIdAndJobPostingId(Long userId, Long jobPostingId);

    boolean existsByUserIdAndJobPostingId(Long userId, Long jobPostingId);

    List<JobBookmark> findAllByUserId(Long userId);
}
