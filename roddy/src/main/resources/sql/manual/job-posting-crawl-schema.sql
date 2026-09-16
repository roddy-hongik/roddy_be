-- 채용공고 수집(크롤링) 스키마 마이그레이션.
--
-- dev 프로파일은 ddl-auto=update 라 엔티티만 바꿔도 반영되지만,
-- prod 는 ddl-auto=validate 이므로 아래 DDL 을 수동으로 적용해야 한다.
--
-- 주의: job_postings 의 행은 전부 수집으로 재생성 가능한 데이터다.
--       기존 행이 남아 있으면 company_code / external_id / apply_url 이 NULL 이라
--       NOT NULL 제약을 걸 수 없으니, 마이그레이션 전에 비우고 재수집하는 것을 권장한다.


-- ============================================================
-- 1) 빈 DB / 신규 환경: 최종 형태로 생성
-- ============================================================

CREATE TABLE IF NOT EXISTS job_postings
(
    job_posting_id     BIGINT       NOT NULL AUTO_INCREMENT,
    company_code       VARCHAR(50)  NOT NULL COMMENT '수집 명세의 company 값 (예: kakao)',
    external_id        VARCHAR(200) NOT NULL COMMENT '수집 원본의 공고 고유 ID',
    company            VARCHAR(255) NOT NULL COMMENT '표시용 회사명 (예: 카카오)',
    title              VARCHAR(500) NOT NULL,
    content            TEXT         NULL COMMENT '상세 수집 전에는 비어 있을 수 있음',
    recruit_field      VARCHAR(200) NULL COMMENT '수집 원본의 직무 분류 문자열',
    desired_job        VARCHAR(50)  NULL COMMENT 'recruit_field 를 로디 직무로 분류한 결과',
    recruit_type       VARCHAR(50)  NULL COMMENT 'INTERN / JUNIOR / SENIOR',
    location           VARCHAR(200) NULL,
    employment_type    VARCHAR(100) NULL,
    apply_url          VARCHAR(1000) NOT NULL,
    posted_at          DATETIME(6)  NULL,
    deadline           DATETIME(6)  NULL COMMENT '상시 채용이면 NULL',
    source_updated_at  DATETIME(6)  NULL COMMENT '수집 원본이 알려준 최종 수정 시각',
    status             VARCHAR(20)  NOT NULL COMMENT 'OPEN / CLOSED',
    closed_at          DATETIME(6)  NULL,
    content_hash       VARCHAR(64)  NULL COMMENT '본문 변경 감지용 SHA-256',
    raw_json           LONGTEXT     NULL COMMENT '수집 원본 레코드 원문',
    crawled_at         DATETIME(6)  NOT NULL,
    created_at         DATETIME(6)  NULL,
    updated_at         DATETIME(6)  NULL,
    PRIMARY KEY (job_posting_id),
    UNIQUE KEY uk_job_posting_source (company_code, external_id),
    KEY idx_job_posting_company_code (company_code),
    KEY idx_job_posting_status_deadline (status, deadline),
    KEY idx_job_posting_desired_job (desired_job)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;


-- ============================================================
-- 2) 기존 job_postings 테이블이 있는 환경: 변경분만 적용
--    (1번 CREATE 로 새로 만든 환경에서는 실행할 필요 없음)
-- ============================================================

-- 2-1. 수집 데이터는 재생성 가능하므로 비우고 시작한다.
-- DELETE FROM recommend_job_postings;
-- DELETE FROM job_bookmarks;
-- DELETE FROM job_postings;

-- 2-2. PK 컬럼명을 컨벤션(<테이블 단수형>_id)에 맞추고,
--      수집 원본의 job_id 와 이름이 겹치지 않게 한다.
-- ALTER TABLE job_postings RENAME COLUMN job_id TO job_posting_id;

-- 2-3. 채용공고 의미에 맞게 기간 컬럼명을 정리한다.
-- ALTER TABLE job_postings RENAME COLUMN start_date TO posted_at;
-- ALTER TABLE job_postings RENAME COLUMN end_date TO deadline;

-- 2-4. 수집 원본 추적 컬럼 추가.
-- ALTER TABLE job_postings
--     ADD COLUMN company_code      VARCHAR(50)   NOT NULL AFTER job_posting_id,
--     ADD COLUMN external_id       VARCHAR(200)  NOT NULL AFTER company_code,
--     ADD COLUMN desired_job       VARCHAR(50)   NULL,
--     ADD COLUMN location          VARCHAR(200)  NULL,
--     ADD COLUMN employment_type   VARCHAR(100)  NULL,
--     ADD COLUMN apply_url         VARCHAR(1000) NOT NULL,
--     ADD COLUMN source_updated_at DATETIME(6)   NULL,
--     ADD COLUMN closed_at         DATETIME(6)   NULL,
--     ADD COLUMN content_hash      VARCHAR(64)   NULL,
--     ADD COLUMN raw_json          LONGTEXT      NULL,
--     ADD COLUMN crawled_at        DATETIME(6)   NOT NULL;

-- 2-5. 수집 원본이 값을 주지 않는 필드의 NOT NULL 을 완화한다.
-- ALTER TABLE job_postings
--     MODIFY COLUMN title         VARCHAR(500) NOT NULL,
--     MODIFY COLUMN content       TEXT         NULL,
--     MODIFY COLUMN recruit_field VARCHAR(200) NULL,
--     MODIFY COLUMN recruit_type  VARCHAR(50)  NULL,
--     MODIFY COLUMN status        VARCHAR(20)  NOT NULL;

-- 2-6. 멱등 적재 키와 조회 인덱스.
-- ALTER TABLE job_postings
--     ADD UNIQUE KEY uk_job_posting_source (company_code, external_id),
--     ADD KEY idx_job_posting_company_code (company_code),
--     ADD KEY idx_job_posting_status_deadline (status, deadline),
--     ADD KEY idx_job_posting_desired_job (desired_job);

-- 2-7. recommend_job_postings 의 FK 컬럼명을 job_bookmarks 와 통일한다.
-- ALTER TABLE recommend_job_postings RENAME COLUMN job_post_id TO job_posting_id;


-- ============================================================
-- 3) 적용 후 점검
-- ============================================================

-- 자연키 중복이 없어야 한다 (0건이면 정상).
-- SELECT company_code, external_id, COUNT(*) AS cnt
-- FROM job_postings
-- GROUP BY company_code, external_id
-- HAVING cnt > 1;
