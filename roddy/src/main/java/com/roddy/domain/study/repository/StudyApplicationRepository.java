package com.roddy.domain.study.repository;

import com.roddy.domain.study.entity.StudyApplication;
import com.roddy.domain.study.enums.StudyApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface StudyApplicationRepository extends JpaRepository<StudyApplication, Long> {

    Optional<StudyApplication> findByStudyPost_IdAndApplicant_Id(Long studyId, Long applicantId);

    @Query("""
            select sa
            from StudyApplication sa
            join fetch sa.applicant
            where sa.studyPost.id = :studyId
            order by sa.createdAt asc
            """)
    List<StudyApplication> findAllByStudyPostIdOrderByCreatedAtAsc(@Param("studyId") Long studyId);

    @Query("""
            select sa
            from StudyApplication sa
            join fetch sa.studyPost sp
            join fetch sp.author
            where sa.id in (
                select innerSa.id
                from StudyApplication innerSa
                where innerSa.applicant.id = :applicantId
                  and (:status is null or innerSa.status = :status)
            )
            """)
    Page<StudyApplication> findMyApplications(
            @Param("applicantId") Long applicantId,
            @Param("status") StudyApplicationStatus status,
            Pageable pageable
    );
}
