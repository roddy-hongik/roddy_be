package com.roddy.domain.auth.handler;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.dto.response.LoginResponse;
import com.roddy.domain.auth.security.CustomOAuth2User;
import com.roddy.domain.auth.service.AuthService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.http.ResponseCookie;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${app.oauth2.redirect-uri}")
    private String frontendRedirectUri;

    @Value("${jwt.expiration.access-token}")
    private long accessTokenTime;

    @Value("${jwt.expiration.refresh-token}")
    private long refreshTokenTime;

    @PostConstruct
    void validateRedirectUri() {
        if (!StringUtils.hasText(frontendRedirectUri)) {
            throw new IllegalStateException("app.oauth2.redirect-uri must be configured.");
        }
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        User user = principal.user();

        LoginResponse tokenResponse = authService.issueTokens(user);
        boolean secureCookie = request.isSecure() || frontendRedirectUri.startsWith("https://");

        addAuthCookie(response, "accessToken", tokenResponse.accessToken(), accessTokenTime, secureCookie);
        addAuthCookie(response, "refreshToken", tokenResponse.refreshToken(), refreshTokenTime, secureCookie);

        String redirectUri = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .queryParam("isOnboard", tokenResponse.isOnboard())
                .queryParam("githubConnected", tokenResponse.githubConnected())
                .build(true)
                .toUriString();

        getRedirectStrategy().sendRedirect(request, response, redirectUri);
    }

    private void addAuthCookie(
            HttpServletResponse response,
            String name,
            String value,
            long maxAgeMillis,
            boolean secure
    ) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAgeMillis / 1000)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
