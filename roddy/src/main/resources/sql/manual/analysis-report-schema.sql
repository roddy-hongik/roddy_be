-- 역량 분석 리포트 스키마.
--
-- dev 프로파일은 ddl-auto=update 라 엔티티만 바꿔도 반영되지만,
-- prod 는 ddl-auto=validate 이므로 아래 DDL 을 수동으로 적용해야 한다.
--
-- 분석은 깃허브를 훑고 LLM 을 부르느라 수십 초가 걸린다. 먼저 PENDING 으로 만들어 두고 끝나면
-- 내용을 채우므로, 내용 컬럼은 완료 전까지 비어 있다.

ALTER TABLE analysis_reports
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT 'PENDING / COMPLETED / FAILED',
    ADD COLUMN failure_reason VARCHAR(1000) NULL COMMENT '분석이 실패한 이유',
    ADD COLUMN analyzed_at DATETIME(6) NULL COMMENT '마지막으로 분석을 끝낸 시각';

-- 기존 행은 이미 내용이 채워져 있으므로 완료로 본다. 기본값은 그 뒤에 뗀다.
ALTER TABLE analysis_reports
    ALTER COLUMN status DROP DEFAULT;

-- 완료 전까지 비어 있는 컬럼들
ALTER TABLE analysis_reports
    MODIFY COLUMN title VARCHAR(255) NULL,
    MODIFY COLUMN summary TEXT NULL,
    MODIFY COLUMN github_analysis TEXT NULL,
    MODIFY COLUMN portfolio_analysis TEXT NULL;

-- 분석이 찾아낸 기술은 로드맵 분류를 아직 정하지 않는다.
-- "Java" 같은 구체적인 기술과 아키텍처·확장성 같은 상위 분류를 자동으로 이을 수 없기 때문이다.
ALTER TABLE stacks
    MODIFY COLUMN stack VARCHAR(255) NULL;


-- ============================================================
-- 적용 후 점검
-- ============================================================

-- 분석 상태 분포. PENDING 이 오래 남아 있으면 분석이 중간에 죽은 것이다.
-- SELECT status, COUNT(*) AS reports, MIN(updated_at) AS oldest
-- FROM analysis_reports
-- GROUP BY status;

-- 실패한 리포트의 이유
-- SELECT user_id, failure_reason, updated_at
-- FROM analysis_reports
-- WHERE status = 'FAILED'
-- ORDER BY updated_at DESC;
