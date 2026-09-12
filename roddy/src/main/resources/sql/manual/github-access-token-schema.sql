-- 깃허브 액세스 토큰 보관 컬럼.
--
-- dev 프로파일은 ddl-auto=update 라 엔티티만 바꿔도 반영되지만,
-- prod 는 ddl-auto=validate 이므로 아래 DDL 을 수동으로 적용해야 한다.
--
-- 값은 AES-GCM 으로 암호화해서 넣는다. 평문이 아니므로 길이에 여유를 둔다.
-- 암호화 키는 TOKEN_SECRET_KEY 환경변수로 주입한다. 키가 바뀌면 기존 토큰은 복호화되지 않으므로,
-- 키를 바꿔야 할 때는 아래 초기화 쿼리로 비우고 사용자가 깃허브를 다시 연결하게 한다.

ALTER TABLE users
    ADD COLUMN github_access_token VARCHAR(512) NULL COMMENT '깃허브 액세스 토큰 (AES-GCM 암호화)';


-- ============================================================
-- 적용 후 점검
-- ============================================================

-- 평문이 섞여 들어가지 않았는지 본다. 깃허브 토큰은 ghu_ / gho_ 로 시작한다.
-- 결과가 나오면 암호화가 걸리지 않은 것이다.
-- SELECT user_id
-- FROM users
-- WHERE github_access_token LIKE 'gh_%'
--    OR github_access_token LIKE 'gho_%'
--    OR github_access_token LIKE 'ghu_%';

-- 암호화 키를 바꿔야 할 때 (기존 토큰은 복호화 불가이므로 비운다)
-- UPDATE users SET github_access_token = NULL, github_connected = false WHERE github_access_token IS NOT NULL;
