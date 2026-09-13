package com.roddy.domain.analysis.dto.response;

import java.util.List;

/**
 * 내 리포트 목록. 끝난 리포트만 최신순으로 싣는다. 진행 중이거나 실패한 분석은 GET /api/analysis/me 로 본다.
 *
 * <p>사용자 한 명의 리포트는 많지 않아 페이지로 나누지 않는다. 나중에 나눌 때 응답 모양이 깨지지 않도록
 * 목록을 필드 하나로 감싸 둔다.
 */
public record AnalysisReportListResponse(List<AnalysisReportSummaryResponse> reports) {
}
