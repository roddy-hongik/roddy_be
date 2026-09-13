package com.roddy.domain.analysis.entity;

import com.roddy.domain.BaseEntity;
import com.roddy.domain.analysis.dto.CompetencyCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리포트 한 건의 평가 축 하나에 대한 점수와 해석.
 *
 * <p>축의 이름과 설명을 code 와 함께 남긴다. 축 정의(YAML)는 나중에 문구가 바뀌거나 축이 빠질 수 있는데,
 * 지난 리포트는 채점했을 때의 축으로 보여야 하기 때문이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
@Table(
        name = "analysis_report_categories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_analysis_report_category", columnNames = {"analysis_report_id", "code"})
)
public class AnalysisReportCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_report_category_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_report_id", nullable = false)
    private AnalysisReport analysisReport;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private int score;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String interpretation;

    public static AnalysisReportCategory create(AnalysisReport analysisReport, CompetencyCategory category,
                                                int score, String interpretation) {
        return AnalysisReportCategory.builder()
                .analysisReport(analysisReport)
                .code(category.code())
                .name(category.name())
                .description(category.description())
                .score(score)
                .interpretation(interpretation == null ? "" : interpretation)
                .build();
    }
}
