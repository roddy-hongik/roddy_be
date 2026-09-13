-- 역량 분석 리포트에 직무별 평가 축과 축별 점수를 싣는다.
--
-- dev 프로파일은 ddl-auto=update 라 엔티티만 바꿔도 반영되지만,
-- prod 는 ddl-auto=validate 이므로 아래 DDL 을 수동으로 적용해야 한다.
-- analysis-report-history-schema.sql 을 먼저 적용한다.
--
-- 평가 축은 직무마다 다르다(resources/analysis/competency-categories.yaml).


-- ============================================================
-- analysis_reports : 분석을 요청한 당시의 직무
-- ============================================================

-- 리포트가 어떤 축으로 채점됐는지 알 수 있도록 직무를 리포트에 남긴다.
ALTER TABLE analysis_reports
    ADD COLUMN desired_job VARCHAR(30) NULL COMMENT '분석을 요청한 당시의 희망 직무. 이 컬럼 이전 리포트는 비어 있다';


-- ============================================================
-- analysis_report_categories : 리포트의 평가 축별 점수
-- ============================================================

-- 축의 이름과 설명을 code 와 함께 남긴다. 축 정의는 나중에 문구가 바뀌거나 축이 빠질 수 있는데,
-- 지난 리포트는 채점했을 때의 축으로 보여야 한다.
-- AI 가 빠뜨린 축은 0점으로 채우지 않고 행을 만들지 않는다.
CREATE TABLE IF NOT EXISTS analysis_report_categories
(
    analysis_report_category_id BIGINT       NOT NULL AUTO_INCREMENT,
    analysis_report_id          BIGINT       NOT NULL,
    code                        VARCHAR(50)  NOT NULL COMMENT '평가 축 code (예: DATA_MODELING)',
    name                        VARCHAR(100) NOT NULL COMMENT '채점했을 때의 축 이름',
    description                 VARCHAR(500) NOT NULL COMMENT '채점했을 때의 축 설명',
    score                       INT          NOT NULL,
    interpretation              TEXT         NOT NULL COMMENT '그 점수를 준 근거와 부족한 점',
    created_at                  DATETIME(6)  NULL,
    updated_at                  DATETIME(6)  NULL,
    PRIMARY KEY (analysis_report_category_id),
    CONSTRAINT uk_analysis_report_category UNIQUE (analysis_report_id, code),
    CONSTRAINT fk_analysis_report_category_report
        FOREIGN KEY (analysis_report_id) REFERENCES analysis_reports (analysis_report_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;


-- ============================================================
-- user_stacks : 기술이 속한 축과 근거를 찾은 곳
-- ============================================================

-- 이 컬럼 이전의 행은 축과 출처를 모른다. 축은 비워 두고, 출처는 둘 다 아니라고 둔다.
ALTER TABLE user_stacks
    ADD COLUMN category_code      VARCHAR(50) NULL COMMENT '속한 평가 축 code',
    ADD COLUMN found_in_github    BIT(1)      NOT NULL DEFAULT b'0' COMMENT '깃허브 저장소에서 근거를 찾았는지',
    ADD COLUMN found_in_portfolio BIT(1)      NOT NULL DEFAULT b'0' COMMENT '포트폴리오에서 근거를 찾았는지';


-- ============================================================
-- 적용 후 점검
-- ============================================================

-- 직무별로 축 점수가 몇 개씩 붙었는지. 축이 빠진 리포트가 많으면 프롬프트를 손봐야 한다.
-- SELECT r.desired_job, COUNT(DISTINCT r.analysis_report_id) AS reports, COUNT(c.analysis_report_category_id) AS category_scores
-- FROM analysis_reports r
--          LEFT JOIN analysis_report_categories c ON c.analysis_report_id = r.analysis_report_id
-- WHERE r.status = 'COMPLETED'
-- GROUP BY r.desired_job;
