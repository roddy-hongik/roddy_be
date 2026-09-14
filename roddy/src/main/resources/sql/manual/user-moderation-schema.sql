-- 어드민이 계정을 정지하고 최근 활동일을 보기 위한 컬럼.
-- suspended_at 이 있는 계정은 로그인·토큰 재발급이 막히고, 이미 받은 토큰으로도 인증되지 않는다.
-- last_login_at 은 토큰을 발급하거나 재발급할 때 갱신한다.
ALTER TABLE users
    ADD COLUMN suspended_at DATETIME(6) NULL,
    ADD COLUMN last_login_at DATETIME(6) NULL;
