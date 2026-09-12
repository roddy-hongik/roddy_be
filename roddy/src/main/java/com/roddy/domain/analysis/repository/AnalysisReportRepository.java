package com.roddy.domain.analysis.repository;

import com.roddy.domain.analysis.entity.AnalysisReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnalysisReportRepository extends JpaRepository<AnalysisReport, Long> {

    Optional<AnalysisReport> findByUserId(Long userId);
}
