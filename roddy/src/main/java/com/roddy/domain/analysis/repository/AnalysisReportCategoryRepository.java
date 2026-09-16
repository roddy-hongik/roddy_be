package com.roddy.domain.analysis.repository;

import com.roddy.domain.analysis.entity.AnalysisReportCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnalysisReportCategoryRepository extends JpaRepository<AnalysisReportCategory, Long> {

    /** 축 정의의 순서대로 저장하므로 저장한 순서가 곧 축의 순서다. 레이더 차트의 축이 리포트마다 흔들리지 않는다. */
    List<AnalysisReportCategory> findAllByAnalysisReportIdOrderByIdAsc(Long analysisReportId);
}
