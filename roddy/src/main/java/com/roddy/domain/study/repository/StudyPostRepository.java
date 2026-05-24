package com.roddy.domain.study.repository;

import com.roddy.domain.study.entity.StudyPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface StudyPostRepository extends JpaRepository<StudyPost, Long>, StudyPostRepositoryCustom {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select sp
            from StudyPost sp
            join fetch sp.author
            where sp.id = :studyId
            """)
    Optional<StudyPost> findByIdForUpdate(@Param("studyId") Long studyId);
}
