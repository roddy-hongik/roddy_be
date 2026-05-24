package com.roddy.domain.onboarding.service;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.onboarding.dto.response.GithubConnectionStatusResponse;
import com.roddy.domain.onboarding.dto.response.GithubOAuthStartResponse;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class GithubOnboardingService {

    private static final String GITHUB_STATE_PREFIX = "GithubOAuthState:";
    private static final Duration GITHUB_STATE_TTL = Duration.ofMinutes(10);
    private static final String GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_ACCESS_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_API_BASE_URL = "https://api.github.com";
    private static final String GITHUB_SCOPE = "read:user";
    private static final Duration GITHUB_API_TIMEOUT = Duration.ofSeconds(5);

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final WebClient webClient = WebClient.builder()
            .baseUrl(GITHUB_API_BASE_URL)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.USER_AGENT, "roddy-backend")
            .build();

    @Value("${app.github.client-id}")
    private String githubClientId;

    @Value("${app.github.client-secret}")
    private String githubClientSecret;

    @Value("${app.github.oauth-redirect-uri}")
    private String githubOAuthRedirectUri;

    @Value("${app.github.frontend-redirect-uri}")
    private String frontendRedirectUri;

    @Transactional(readOnly = true)
    public GithubConnectionStatusResponse getConnectionStatus(Long userId) {
        User user = getUser(userId);
        return new GithubConnectionStatusResponse(
                user.isOnboarded(),
                user.isGithubConnected(),
                user.getGithubId(),
                user.getGithubUrl()
        );
    }

    public GithubOAuthStartResponse createAuthorizationUrl(Long userId) {
        String state = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(getStateKey(state), String.valueOf(userId), GITHUB_STATE_TTL);

        String authorizationUrl = UriComponentsBuilder.fromUriString(GITHUB_AUTHORIZE_URL)
                .queryParam("client_id", githubClientId)
                .queryParam("redirect_uri", githubOAuthRedirectUri)
                .queryParam("scope", GITHUB_SCOPE)
                .queryParam("state", state)
                .build(true)
                .toUriString();

        return new GithubOAuthStartResponse(authorizationUrl);
    }

    @Transactional
    public String connectGithub(String code, String state) {
        String storedUserId = redisTemplate.opsForValue().get(getStateKey(state));
        if (storedUserId == null) {
            return buildFrontendRedirect("error", "invalid_state");
        }

        redisTemplate.delete(getStateKey(state));

        try {
            Long userId = Long.valueOf(storedUserId);
            GithubTokenResponse tokenResponse = requestAccessToken(code);
            GithubUserProfile githubUserProfile = fetchGithubUserProfile(tokenResponse.accessToken());

            User user = getUser(userId);
            user.connectGithub(String.valueOf(githubUserProfile.id()), githubUserProfile.htmlUrl());

            return buildFrontendRedirect("success", "github_connected");
        } catch (GeneralException exception) {
            return buildFrontendRedirect("error", exception.getCode().getCode());
        } catch (Exception exception) {
            return buildFrontendRedirect("error", "github_connection_failed");
        }
    }

    private GithubTokenResponse requestAccessToken(String code) {
        GithubTokenResponse response;
        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("client_id", githubClientId);
            formData.add("client_secret", githubClientSecret);
            formData.add("code", code);
            formData.add("redirect_uri", githubOAuthRedirectUri);

            response = WebClient.builder()
                    .baseUrl(GITHUB_ACCESS_TOKEN_URL)
                    .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .defaultHeader(HttpHeaders.USER_AGENT, "roddy-backend")
                    .build()
                    .post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(GithubTokenResponse.class)
                    .block(GITHUB_API_TIMEOUT);
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof TimeoutException) {
                throw new GeneralException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, "GitHub 액세스 토큰 요청 시간이 초과되었습니다.");
            }
            throw exception;
        }

        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new GeneralException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, "GitHub 액세스 토큰 발급에 실패했습니다.");
        }

        return response;
    }

    private GithubUserProfile fetchGithubUserProfile(String accessToken) {
        GithubUserProfile response;
        try {
            response = webClient.get()
                    .uri("/user")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .bodyToMono(GithubUserProfile.class)
                    .block(GITHUB_API_TIMEOUT);
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof TimeoutException) {
                throw new GeneralException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, "GitHub 사용자 정보 요청 시간이 초과되었습니다.");
            }
            throw exception;
        }

        if (response == null || response.id() == null || response.htmlUrl() == null || response.htmlUrl().isBlank()) {
            throw new GeneralException(GeneralErrorCode.EXTERNAL_SERVICE_TIMEOUT, "GitHub 사용자 정보를 불러오지 못했습니다.");
        }

        return response;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));
    }

    private String getStateKey(String state) {
        return GITHUB_STATE_PREFIX + state;
    }

    private String buildFrontendRedirect(String status, String reason) {
        return UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("status", status)
                .queryParam("reason", reason)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUriString();
    }

    private record GithubTokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token")
            String accessToken
    ) {
    }

    private record GithubUserProfile(
            Long id,
            @com.fasterxml.jackson.annotation.JsonProperty("html_url")
            String htmlUrl
    ) {
    }
}
