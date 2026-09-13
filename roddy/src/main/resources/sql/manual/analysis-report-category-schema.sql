-- 역량 분석 리포트에 직무별 평가 축을 싣는다.
--
-- dev 프로파일은 ddl-auto=update 라 엔티티만 바꿔도 반영되지만,
-- prod 는 ddl-auto=validate 이므로 아래 DDL 을 수동으로 적용해야 한다.
-- analysis-report-history-schema.sql 을 먼저 적용한다.
--
-- 평가 축은 직무마다 다르다(resources/analysis/competency-categories.yaml).
-- 리포트가 어떤 축으로 채점됐는지 알 수 있도록, 분석을 요청한 당시의 직무를 리포트에 남긴다.

ALTER TABLE analysis_reports
    ADD COLUMN desired_job VARCHAR(30) NULL COMMENT '분석을 요청한 당시의 희망 직무. 이 컬럼 이전 리포트는 비어 있다';
