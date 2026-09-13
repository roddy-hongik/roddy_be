package com.roddy.domain.mypage.dto.response;

/**
 * 프로필 이미지를 S3 에 직접 올릴 주소.
 *
 * @param uploadUrl   PUT 할 주소. contentType 과 같은 Content-Type 헤더를 붙여야 서명이 맞는다
 * @param objectKey   올린 뒤 프로필 수정(PATCH /api/mypage/profile)에 보낼 키
 * @param contentType 파일 확장자로 정한 값 (image/png, image/jpeg)
 */
public record ProfileImagePresignResponse(
        String uploadUrl,
        String objectKey,
        String contentType,
        long expiresInMinutes
) {
}
