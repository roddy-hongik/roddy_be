package com.roddy.domain.auth.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.roddy.domain.auth.dto.response.LoginResponse;
import com.roddy.domain.auth.dto.response.SocialLoginResponse;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.enums.SocialType;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private static final Duration SOCIAL_API_TIMEOUT = Duration.ofSeconds(5);
    private static final String GOOGLE_BASE_URL = "https://openidconnect.googleapis.com";
    private static final String KAKAO_BASE_URL = "https://kapi.kakao.com";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    private final WebClient googleWebClient = WebClient.builder()
            .baseUrl(GOOGLE_BASE_URL)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.USER_AGENT, "roddy-backend")
            .build();

    private final WebClient kakaoWebClient = WebClient.builder()
            .baseUrl(KAKAO_BASE_URL)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.USER_AGENT, "roddy-backend")
            .build();

    @Transactional
    public SocialLoginResponse loginWithGoogle(String providerAccessToken) {
        GoogleUserInfo googleUserInfo = fetchGoogleUserInfo(providerAccessToken);

        if (googleUserInfo.email() == null || googleUserInfo.sub() == null) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_TOKEN,
                    "구글 사용자 정보를 불러오지 못했습니다."
            );
        }

        if (!Boolean.TRUE.equals(googleUserInfo.emailVerified())) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이메일 인증이 완료된 구글 계정만 사용할 수 있습니다."
            );
        }

        User user = resolveOrCreateSocialUser(
                SocialType.GOOGLE,
                googleUserInfo.sub(),
                googleUserInfo.email(),
                defaultDisplayName(googleUserInfo.name(), googleUserInfo.email())
        );

        LoginResponse loginResponse = authService.issueTokens(user);
        return SocialLoginResponse.from(loginResponse, user);
    }

    @Transactional
    public SocialLoginResponse loginWithKakao(String providerAccessToken) {
        KakaoUserInfo kakaoUserInfo = fetchKakaoUserInfo(providerAccessToken);
        KakaoAccount kakaoAccount = kakaoUserInfo.kakaoAccount();

        String email = kakaoAccount == null ? null : kakaoAccount.email();
        String socialId = kakaoUserInfo.id() == null ? null : String.valueOf(kakaoUserInfo.id());

        if (email == null || socialId == null) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "카카오 계정 이메일 제공 동의가 필요합니다."
            );
        }

        if (!Boolean.TRUE.equals(kakaoAccount.isEmailValid()) || !Boolean.TRUE.equals(kakaoAccount.isEmailVerified())) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이메일 인증이 완료된 카카오 계정만 사용할 수 있습니다."
            );
        }

        User user = resolveOrCreateSocialUser(
                SocialType.KAKAO,
                socialId,
                email,
                extractKakaoDisplayName(kakaoUserInfo, email)
        );

        LoginResponse loginResponse = authService.issueTokens(user);
        return SocialLoginResponse.from(loginResponse, user);
    }

    User resolveOrCreateSocialUser(
            SocialType socialType,
            String socialId,
            String email,
            String name
    ) {
        return userRepository.findBySocialTypeAndSocialIdAndDeletedAtIsNull(socialType, socialId)
                .orElseGet(() -> userRepository.findByEmailAndDeletedAtIsNull(email)
                        .map(existingUser -> validateExistingSocialUser(existingUser, socialType))
                        .orElseGet(() -> userRepository.save(
                                User.createSocialUser(
                                        name,
                                        email,
                                        createDummyPassword(),
                                        socialType,
                                        socialId
                                )
                        )));
    }

    private User validateExistingSocialUser(User existingUser, SocialType socialType) {
        if (existingUser.getSocialType() == socialType) {
            return existingUser;
        }

        if (existingUser.getSocialType() == SocialType.LOCAL) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_PARAMETER,
                    "이미 이메일/비밀번호로 가입된 계정입니다. 로컬 로그인을 이용해주세요."
            );
        }

        throw new GeneralException(
                GeneralErrorCode.INVALID_PARAMETER,
                "이미 " + toKoreanProviderName(existingUser.getSocialType()) + " 계정으로 가입된 이메일입니다. 해당 소셜 로그인을 이용해주세요."
        );
    }

    private GoogleUserInfo fetchGoogleUserInfo(String providerAccessToken) {
        try {
            GoogleUserInfo response = googleWebClient.get()
                    .uri("/v1/userinfo")
                    .headers(headers -> headers.setBearerAuth(providerAccessToken))
                    .retrieve()
                    .bodyToMono(GoogleUserInfo.class)
                    .block(SOCIAL_API_TIMEOUT);

            if (response == null) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_TOKEN,
                        "구글 사용자 정보를 불러오지 못했습니다."
                );
            }

            return response;
        } catch (WebClientResponseException exception) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_TOKEN,
                    "구글 액세스 토큰이 유효하지 않습니다."
            );
        } catch (WebClientRequestException exception) {
            throw new GeneralException(
                    GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT,
                    "구글 사용자 정보 요청에 실패했습니다."
            );
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof TimeoutException) {
                throw new GeneralException(
                        GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT,
                        "구글 사용자 정보 요청 시간이 초과되었습니다."
                );
            }
            throw exception;
        }
    }

    private KakaoUserInfo fetchKakaoUserInfo(String providerAccessToken) {
        try {
            KakaoUserInfo response = kakaoWebClient.get()
                    .uri("/v2/user/me")
                    .headers(headers -> headers.setBearerAuth(providerAccessToken))
                    .retrieve()
                    .bodyToMono(KakaoUserInfo.class)
                    .block(SOCIAL_API_TIMEOUT);

            if (response == null) {
                throw new GeneralException(
                        GeneralErrorCode.INVALID_TOKEN,
                        "카카오 사용자 정보를 불러오지 못했습니다."
                );
            }

            return response;
        } catch (WebClientResponseException exception) {
            throw new GeneralException(
                    GeneralErrorCode.INVALID_TOKEN,
                    "카카오 액세스 토큰이 유효하지 않습니다."
            );
        } catch (WebClientRequestException exception) {
            throw new GeneralException(
                    GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT,
                    "카카오 사용자 정보 요청에 실패했습니다."
            );
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof TimeoutException) {
                throw new GeneralException(
                        GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT,
                        "카카오 사용자 정보 요청 시간이 초과되었습니다."
                );
            }
            throw exception;
        }
    }

    private String extractKakaoDisplayName(KakaoUserInfo kakaoUserInfo, String fallbackEmail) {
        KakaoAccount kakaoAccount = kakaoUserInfo.kakaoAccount();
        if (kakaoAccount != null && kakaoAccount.profile() != null && hasText(kakaoAccount.profile().nickname())) {
            return kakaoAccount.profile().nickname();
        }

        if (kakaoUserInfo.properties() != null && hasText(kakaoUserInfo.properties().nickname())) {
            return kakaoUserInfo.properties().nickname();
        }

        return fallbackEmail;
    }

    private String defaultDisplayName(String candidate, String fallbackEmail) {
        return hasText(candidate) ? candidate : fallbackEmail;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String createDummyPassword() {
        return passwordEncoder.encode(UUID.randomUUID().toString());
    }

    private String toKoreanProviderName(SocialType socialType) {
        return switch (socialType) {
            case GOOGLE -> "구글";
            case KAKAO -> "카카오";
            case LOCAL -> "로컬";
        };
    }

    private record GoogleUserInfo(
            String sub,
            String email,
            String name,
            @JsonProperty("email_verified")
            Boolean emailVerified
    ) {
    }

    private record KakaoUserInfo(
            Long id,
            @JsonProperty("kakao_account")
            KakaoAccount kakaoAccount,
            KakaoProperties properties
    ) {
    }

    private record KakaoAccount(
            String email,
            @JsonProperty("is_email_valid")
            Boolean isEmailValid,
            @JsonProperty("is_email_verified")
            Boolean isEmailVerified,
            KakaoProfile profile
    ) {
    }

    private record KakaoProfile(
            String nickname
    ) {
    }

    private record KakaoProperties(
            String nickname
    ) {
    }
}
