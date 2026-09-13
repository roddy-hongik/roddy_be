-- 역량 분석 리포트를 사용자당 하나에서, 분석할 때마다 쌓는 구조로 바꾼다.
--
-- prod 는 ddl-auto=validate 이므로 아래 DDL 을 수동으로 적용해야 한다.
-- dev 는 ddl-auto=update 지만 update 는 기존 유니크 제약을 지우지 않으므로 dev 에도 적용해야 한다.
-- 지우지 않고 두면 두 번째 분석부터 유니크 제약에 걸려 저장이 실패한다.
--
-- 지금까지는 다시 분석하면 리포트를 덮어쓰고 기술스택도 통째로 갈아끼웠다.
-- 기존 행은 그대로 두면 사용자마다 리포트 한 건과 거기 딸린 기술스택이 된다.


-- ============================================================
-- analysis_reports : user_id 유니크 해제
-- ============================================================

-- user_id 외래 키가 유니크 인덱스를 쓰고 있을 수 있다. 대신 쓸 인덱스를 먼저 만든다.
CREATE INDEX idx_analysis_report_user ON analysis_reports (user_id, analysis_report_id);

-- 유니크 인덱스 이름은 Hibernate 가 지어서 환경마다 다르다. 이름을 찾아 지운다.
SET @unique_index = (
    SELECT INDEX_NAME
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'analysis_reports'
      AND COLUMN_NAME = 'user_id'
      AND NON_UNIQUE = 0
    LIMIT 1
);
SET @drop_unique_index = IF(@unique_index IS NULL,
                            'SELECT 1',
                            CONCAT('ALTER TABLE analysis_reports DROP INDEX `', @unique_index, '`'));
PREPARE drop_unique_index FROM @drop_unique_index;
EXECUTE drop_unique_index;
DEALLOCATE PREPARE drop_unique_index;


-- ============================================================
-- user_stacks : 기술스택을 사용자가 아니라 리포트 단위로 유일하게
-- ============================================================

-- 같은 기술도 리포트마다 따로 남긴다.
-- user_id 외래 키가 기존 유니크 인덱스(uk_user_stack)를 쓰고 있으므로, 대신 쓸 인덱스를 먼저 만든다.
CREATE INDEX idx_user_stack_user ON user_stacks (user_id);

ALTER TABLE user_stacks
    DROP INDEX uk_user_stack,
    ADD CONSTRAINT uk_user_stack_report UNIQUE (analysis_report_id, stack_detail_id);


-- ============================================================
-- 적용 후 점검
-- ============================================================

-- 유니크 인덱스가 남아 있지 않아야 한다. PRIMARY 와 uk_user_stack_report 만 나와야 한다.
-- SELECT TABLE_NAME, INDEX_NAME, COLUMN_NAME
-- FROM information_schema.STATISTICS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME IN ('analysis_reports', 'user_stacks')
--   AND NON_UNIQUE = 0;

-- 진행 중인 리포트가 둘 이상인 사용자. 한 번에 하나만 분석하므로 나오면 안 된다.
-- SELECT user_id, COUNT(*) AS pending_reports
-- FROM analysis_reports
-- WHERE status = 'PENDING'
-- GROUP BY user_id
-- HAVING COUNT(*) > 1;
