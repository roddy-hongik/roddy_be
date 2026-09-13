-- 프로필 이미지를 주소가 아니라 S3 객체 키로 저장한다.
--
-- dev 프로파일은 ddl-auto=update 라 엔티티만 바꿔도 반영되지만,
-- prod 는 ddl-auto=validate 이므로 아래 DDL 을 수동으로 적용해야 한다.
--
-- 지금까지 profile_image_url 에는 프론트가 보낸 문자열이 그대로 들어갔다. 브라우저의 blob: 주소처럼
-- 다른 곳에서는 열리지 않는 값이라 새 컬럼으로 옮기지 않는다. 사용자가 이미지를 다시 올려야 한다.

ALTER TABLE users
    ADD COLUMN profile_image_object_key VARCHAR(255) NULL COMMENT '프로필 이미지의 S3 객체 키 (profile-image/{userId}/...)';


-- ============================================================
-- 적용 후 정리
-- ============================================================

-- profile_image_url 은 더 이상 읽지 않는다. 남은 값을 확인한 뒤 지운다.
-- SELECT user_id, profile_image_url FROM users WHERE profile_image_url IS NOT NULL;
-- ALTER TABLE users DROP COLUMN profile_image_url;
