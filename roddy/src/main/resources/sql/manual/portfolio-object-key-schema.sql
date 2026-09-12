-- 포트폴리오는 주소 대신 객체 키를 저장한다.
--
-- 지금까지 users.portfolio_url 에는 온보딩 때 만든 presigned 주소가 그대로 들어갔다.
-- 그 주소는 몇 분이면 만료되므로 마이페이지의 포트폴리오 링크가 곧 깨지고, 분석도 파일을 읽지 못한다.
-- 이제 객체 키를 저장하고 볼 때마다 새 주소를 만든다.
--
-- dev 프로파일은 ddl-auto=update 라 엔티티만 바꿔도 반영되지만,
-- prod 는 ddl-auto=validate 이므로 아래 DDL 을 수동으로 적용해야 한다.

ALTER TABLE users
    ADD COLUMN portfolio_object_key VARCHAR(255) NULL COMMENT '포트폴리오 파일의 S3 객체 키';


-- ============================================================
-- 기존 데이터 옮기기
-- ============================================================

-- presigned 주소의 경로 부분이 곧 객체 키다.
--   https://bucket.s3.region.amazonaws.com/portfolio/1/uuid.pdf?X-Amz-...
--                                          ^^^^^^^^^^^^^^^^^^^^^ 이 부분
-- 주소는 만료됐어도 키는 그대로 쓸 수 있다.
--
-- UPDATE users
-- SET portfolio_object_key = SUBSTRING_INDEX(
--         SUBSTRING_INDEX(portfolio_url, '?', 1),
--         '.amazonaws.com/', -1)
-- WHERE portfolio_url LIKE '%.amazonaws.com/%'
--   AND portfolio_object_key IS NULL;

-- 옮긴 결과 확인 (portfolio/ 로 시작해야 정상)
-- SELECT user_id, portfolio_object_key
-- FROM users
-- WHERE portfolio_object_key IS NOT NULL
--   AND portfolio_object_key NOT LIKE 'portfolio/%';


-- ============================================================
-- 옮긴 뒤 정리 (확인하고 나서)
-- ============================================================

-- ALTER TABLE users DROP COLUMN portfolio_url;
