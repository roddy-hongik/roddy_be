package com.roddy.domain.analysis.repository;

import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.analysis.enums.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 리포트의 선후는 id 로 가린다. 한 사용자는 한 번에 하나만 분석하므로 요청한 순서와 끝난 순서가 같다.
 */
public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {

    /** 가장 최근에 요청한 분석. 진행 중이거나 실패한 것일 수 있다. */
    Optional<AnalysisReport> findFirstByUserIdOrderByIdDesc(Long userId);

    Optional<AnalysisReport> findFirstByUserIdAndStatusOrderByIdDesc(Long userId, AnalysisStatus status);

    boolean existsByUserIdAndStatus(Long userId, AnalysisStatus status);
}
