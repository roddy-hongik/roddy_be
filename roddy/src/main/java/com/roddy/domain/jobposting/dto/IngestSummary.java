package com.roddy.domain.jobposting.dto;

/**
 * 회사 한 곳의 적재 결과.
 *
 * @param collected 수집된 공고 수
 * @param created   새로 저장한 공고 수
 * @param updated   내용이 바뀌어 갱신한 공고 수
 * @param unchanged 내용이 그대로라 수집 시각만 갱신한 공고 수
 * @param closed    마감으로 처리한 공고 수
 * @param failed    적재하지 못한 공고 수 (필수 값 누락 등)
 */
public record IngestSummary(int collected, int created, int updated, int unchanged, int closed, int failed) {

    public static IngestSummary empty() {
        return new IngestSummary(0, 0, 0, 0, 0, 0);
    }

    public boolean hasFailure() {
        return failed > 0;
    }
}
