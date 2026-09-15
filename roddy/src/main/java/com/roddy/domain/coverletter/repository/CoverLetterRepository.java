package com.roddy.domain.coverletter.repository;

import com.roddy.domain.coverletter.entity.CoverLetter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CoverLetterRepository extends JpaRepository<CoverLetter, Long> {
    @EntityGraph(attributePaths = "jobPosting")
    Page<CoverLetter> findAllByUserId(Long userId, Pageable pageable);
    @EntityGraph(attributePaths = {"jobPosting", "answers"})
    Optional<CoverLetter> findByIdAndUserId(Long id, Long userId);
}
