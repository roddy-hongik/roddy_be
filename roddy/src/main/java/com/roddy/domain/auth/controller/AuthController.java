package com.roddy.domain.auth.controller;

import com.roddy.domain.auth.dto.request.LoginRequest;
import com.roddy.domain.auth.dto.request.LogoutRequest;
import com.roddy.domain.auth.dto.request.ReissueTokenRequest;
import com.roddy.domain.auth.dto.request.SignupRequest;
import com.roddy.domain.auth.dto.request.SocialLoginRequest;
import com.roddy.domain.auth.dto.response.LoginResponse;
import com.roddy.domain.auth.dto.response.ReissueTokenResponse;
import com.roddy.domain.auth.dto.response.SocialLoginResponse;
import com.roddy.domain.auth.service.AuthService;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "회원가입, 로그인, 소셜 로그인, 토큰 재발급, 로그아웃 API")
public class AuthController {

    private final AuthService authService;
    private final SocialAuthService socialAuthService;

    @PostMapping("/signup")
    @Operation(
            summary = "회원가입",
            description = "이름, 이메일, 비밀번호로 로컬 계정을 생성합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원가입 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 또는 중복 이메일", content = @Content(schema = @Schema())),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "서버 내부 오류", content = @Content(schema = @Schema()))
    })
    public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ApiResponse.onSuccess("회원가입이 완료되었습니다.");
    }

    @PostMapping("/login")
    @Operation(
            summary = "로컬 로그인",
            description = "이메일과 비밀번호로 로그인하고 액세스 토큰과 리프레시 토큰을 발급합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치", content = @Content(schema = @Schema())),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "소셜 로그인 전용 계정", content = @Content(schema = @Schema()))
    })
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.onSuccess("로그인이 완료되었습니다.", authService.login(request));
    }

    @PostMapping("/google")
    @Operation(
            summary = "구글 소셜 로그인",
            description = "프론트에서 발급받은 구글 액세스 토큰으로 사용자 정보를 조회한 뒤 Roddy 토큰과 사용자 정보를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "구글 로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이메일 미동의 또는 다른 로그인 방식으로 가입된 계정", content = @Content(schema = @Schema())),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 구글 액세스 토큰", content = @Content(schema = @Schema())),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "504", description = "구글 사용자 정보 조회 타임아웃", content = @Content(schema = @Schema()))
    })
    public ApiResponse<SocialLoginResponse> googleLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ApiResponse.onSuccess("구글 로그인이 완료되었습니다.", socialAuthService.loginWithGoogle(request.accessToken()));
    }

    @PostMapping("/kakao")
    @Operation(
            summary = "카카오 소셜 로그인",
            description = "프론트에서 발급받은 카카오 액세스 토큰으로 사용자 정보를 조회한 뒤 Roddy 토큰과 사용자 정보를 반환합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "카카오 로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "이메일 미동의 또는 다른 로그인 방식으로 가입된 계정", content = @Content(schema = @Schema())),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 카카오 액세스 토큰", content = @Content(schema = @Schema())),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "504", description = "카카오 사용자 정보 조회 타임아웃", content = @Content(schema = @Schema()))
    })
    public ApiResponse<SocialLoginResponse> kakaoLogin(@Valid @RequestBody SocialLoginRequest request) {
        return ApiResponse.onSuccess("카카오 로그인이 완료되었습니다.", socialAuthService.loginWithKakao(request.accessToken()));
    }

    @PostMapping("/reissue")
    @Operation(
            summary = "토큰 재발급",
            description = "만료된 액세스 토큰과 유효한 리프레시 토큰을 받아 새 토큰 쌍을 발급합니다. 리프레시 토큰은 Redis에 저장된 값과 일치해야 합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 재발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 토큰", content = @Content(schema = @Schema())),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "동시 재발급 요청 처리 중", content = @Content(schema = @Schema()))
    })
    public ApiResponse<ReissueTokenResponse> reissue(@Valid @RequestBody ReissueTokenRequest request) {
        return ApiResponse.onSuccess("토큰이 재발급되었습니다.", authService.reissueToken(request));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "로그아웃",
            description = "리프레시 토큰을 삭제하고, 남은 액세스 토큰 유효 시간 동안 해당 액세스 토큰을 Redis 블랙리스트에 등록합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 토큰", content = @Content(schema = @Schema()))
    })
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.onSuccess("로그아웃이 완료되었습니다.");
    }
}
