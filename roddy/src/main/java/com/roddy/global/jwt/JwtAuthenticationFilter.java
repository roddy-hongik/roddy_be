package com.roddy.global.jwt;

import com.roddy.global.security.UserDetailsServiceImpl;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j(topic = "JWT 검증 및 인가")
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BLACKLIST_PREFIX = "Blacklist:";

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String tokenValue = request.getHeader(JwtUtil.AUTHORIZATION_HEADER);

        if (StringUtils.hasText(tokenValue) && tokenValue.startsWith(JwtUtil.BEARER_PREFIX)) {
            String token = jwtUtil.substringToken(tokenValue);

            if (Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token))) {
                log.info("블랙리스트 처리된 액세스 토큰입니다.");
                filterChain.doFilter(request, response);
                return;
            }

            if (jwtUtil.validateToken(token)) {
                try {
                    Claims claims = jwtUtil.getClaimsFromToken(token);
                    String email = jwtUtil.getEmailFromToken(claims);
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                    if (userDetails.isAccountNonLocked()) {
                        Authentication authentication = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                        SecurityContext context = SecurityContextHolder.getContext();
                        context.setAuthentication(authentication);
                        SecurityContextHolder.setContext(context);

                        log.info("사용자 인증 성공: email = {}", email);
                    } else {
                        // 어드민이 정지한 계정은 정지 전에 받은 토큰으로도 인증하지 않는다.
                        log.info("정지된 계정의 토큰입니다. email = {}", email);
                    }
                } catch (UsernameNotFoundException e) {
                    SecurityContextHolder.clearContext();
                    log.debug("JWT subject user not found");
                } catch (RuntimeException e) {
                    SecurityContextHolder.clearContext();
                    log.debug("JWT authentication skipped due to runtime exception", e);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
