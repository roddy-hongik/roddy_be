package com.roddy.domain.auth.dto.response;

import com.roddy.domain.enums.Role;
import lombok.Builder;

/**
 * @param role 프론트가 어드민 화면을 보여줄지 정하는 데 쓴다. 권한 검사는 서버가 요청마다 따로 한다
 */
@Builder
public record LoginResponse(
        String accessToken,
        String refreshToken,
        boolean isOnboard,
        boolean githubConnected,
        Role role
) {
}
