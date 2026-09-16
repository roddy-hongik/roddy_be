package com.roddy.domain.mypage.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * @param fileName 올릴 파일 이름. 확장자로 png / jpg 인지 본다
 */
public record ProfileImagePresignRequest(
        @NotBlank
        String fileName
) {
}
