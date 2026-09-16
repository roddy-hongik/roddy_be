-- 채용공고 요구 기술스택 테이블.
--
-- dev 프로파일은 ddl-auto=update 라 엔티티만 바꿔도 반영되지만,
-- prod 는 ddl-auto=validate 이므로 아래 DDL 을 수동으로 적용해야 한다.
--
-- 값은 공고 글에서 사전 매칭으로 뽑아낸 것이라 언제든 다시 채울 수 있다.
-- 사전(resources/jobposting/tech-stacks.yaml)을 늘린 뒤 다음 수집이 돌면 기존 공고도 따라 갱신된다.

CREATE TABLE IF NOT EXISTS job_posting_tech_stacks
(
    job_posting_id BIGINT      NOT NULL,
    tech_stack     VARCHAR(50) NOT NULL COMMENT '사전에 등록된 기술 이름 (예: Spring Boot)',
    PRIMARY KEY (job_posting_id, tech_stack),
    CONSTRAINT fk_job_posting_tech_stack_posting
        FOREIGN KEY (job_posting_id) REFERENCES job_postings (job_posting_id),
    KEY idx_job_posting_tech_stack (tech_stack)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;


-- ============================================================
-- 적용 후 점검
-- ============================================================

-- 많이 요구되는 기술 순. 사전이 실제 공고를 얼마나 덮는지 감을 잡는 용도.
-- SELECT tech_stack, COUNT(*) AS postings
-- FROM job_posting_tech_stacks
-- GROUP BY tech_stack
-- ORDER BY postings DESC
-- LIMIT 30;

-- 본문은 있는데 기술스택이 하나도 안 잡힌 공고. 사전에 빠진 기술을 찾는 실마리가 된다.
-- SELECT jp.company, jp.title, CHAR_LENGTH(jp.content) AS content_length
-- FROM job_postings jp
--          LEFT JOIN job_posting_tech_stacks ts ON ts.job_posting_id = jp.job_posting_id
-- WHERE jp.content IS NOT NULL
--   AND ts.job_posting_id IS NULL
-- ORDER BY content_length DESC
-- LIMIT 30;
