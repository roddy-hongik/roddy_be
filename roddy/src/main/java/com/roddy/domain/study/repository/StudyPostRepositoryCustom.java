package com.roddy.domain.study.repository;

import com.roddy.domain.study.dto.request.StudySearchCondition;
import com.roddy.domain.study.entity.StudyPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface StudyPostRepositoryCustom {

    Page<StudyPost> search(StudySearchCondition condition, Pageable pageable);

    Optional<StudyPost> findDetailById(Long studyId);
}
