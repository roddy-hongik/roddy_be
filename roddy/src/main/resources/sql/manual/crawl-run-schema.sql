-- 채용공고 수집 이력 테이블.
--
-- dev 프로파일은 ddl-auto=update 라 엔티티만 바꿔도 반영되지만,
-- prod 는 ddl-auto=validate 이므로 아래 DDL 을 수동으로 적용해야 한다.

CREATE TABLE IF NOT EXISTS crawl_runs
(
    crawl_run_id        BIGINT      NOT NULL AUTO_INCREMENT,
    company_code        VARCHAR(50) NOT NULL COMMENT '수집 명세의 company 값 (예: kakao)',
    status              VARCHAR(20) NOT NULL COMMENT 'SUCCESS / PARTIAL / FAILED',
    collected_count     INT         NOT NULL COMMENT '수집된 공고 수',
    created_count       INT         NOT NULL COMMENT '새로 저장한 공고 수',
    updated_count       INT         NOT NULL COMMENT '내용이 바뀌어 갱신한 공고 수',
    unchanged_count     INT         NOT NULL COMMENT '내용이 그대로라 수집 시각만 갱신한 공고 수',
    closed_count        INT         NOT NULL COMMENT '마감으로 처리한 공고 수',
    failed_count        INT         NOT NULL COMMENT '필수 값이 없어 적재하지 못한 공고 수',
    detail_failed_count INT         NOT NULL COMMENT '상세 페이지를 받아오지 못한 공고 수',
    started_at          DATETIME(6) NOT NULL,
    finished_at         DATETIME(6) NOT NULL,
    message             VARCHAR(2000) NULL COMMENT '점검에서 걸린 문제나 예외 메시지',
    created_at          DATETIME(6) NULL,
    updated_at          DATETIME(6) NULL,
    PRIMARY KEY (crawl_run_id),
    KEY idx_crawl_run_company_started (company_code, started_at),
    KEY idx_crawl_run_started_at (started_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;


-- ============================================================
-- 적용 후 점검
-- ============================================================

-- 회사별 마지막 수집 결과. 어드민 수집 현황이 보여줄 내용과 같다.
-- SELECT r.company_code, r.status, r.collected_count, r.created_count,
--        r.closed_count, r.failed_count, r.started_at, r.message
-- FROM crawl_runs r
--          JOIN (SELECT company_code, MAX(started_at) AS last_started
--                FROM crawl_runs
--                GROUP BY company_code) latest
--               ON r.company_code = latest.company_code
--                   AND r.started_at = latest.last_started
-- ORDER BY r.status, r.company_code;

-- 계속 깨지고 있는 회사 (최근 7일).
-- SELECT company_code, COUNT(*) AS runs,
--        SUM(status = 'FAILED') AS failed,
--        SUM(collected_count = 0) AS empty_runs
-- FROM crawl_runs
-- WHERE started_at >= NOW() - INTERVAL 7 DAY
-- GROUP BY company_code
-- HAVING failed > 0 OR empty_runs > 0;
