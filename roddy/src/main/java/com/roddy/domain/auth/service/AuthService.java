package com.roddy.domain.auth.service;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.dto.request.LoginRequest;
import com.roddy.domain.auth.dto.request.LogoutRequest;
import com.roddy.domain.auth.dto.request.ReissueTokenRequest;
import com.roddy.domain.auth.dto.request.SignupRequest;
import com.roddy.domain.auth.dto.response.LoginResponse;
import com.roddy.domain.auth.dto.response.ReissueTokenResponse;
import com.roddy.domain.enums.SocialType;
import com.roddy.global.apiPayload.code.GeneralErrorCode;
import com.roddy.global.apiPayload.exception.GeneralException;
import com.roddy.global.jwt.JwtUtil;
import com.roddy.domain.auth.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String REFRESH_TOKEN_PREFIX = "RefreshToken:";
    private static final String BLACKLIST_PREFIX = "Blacklist:";
    private static final String REISSUE_LOCK_PREFIX = "ReissueLock:";
    private static final long REISSUE_LOCK_TIMEOUT_SECONDS = 3L;
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = createCompareAndDeleteScript();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new GeneralException(GeneralErrorCode.DUPLICATE_LOGINID, "이미 존재하는 이메일입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.signup(request.name(), request.email(), encodedPassword);

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new GeneralException(GeneralErrorCode.DUPLICATE_LOGINID, "이미 존재하는 이메일입니다.");
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.INVALID_LOGIN, "이메일과 비밀번호를 확인해주세요."));

        if (user.getSocialType() != SocialType.LOCAL) {
            throw new GeneralException(
                    GeneralErrorCode.SOCIAL_LOGIN_REQUIRED,
                    "Google 계정으로 연동된 이메일입니다. 소셜 로그인을 이용해주세요"
            );
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new GeneralException(GeneralErrorCode.INVALID_LOGIN, "이메일과 비밀번호를 확인해주세요.");
        }

        return issueTokens(user);
    }

    public LoginResponse issueTokens(User user) {
        String accessToken = jwtUtil.createAccessToken(user.getEmail(), user.getId());
        String refreshTokenValue = jwtUtil.createRefreshToken(user.getEmail());

        saveRefreshToken(user.getId(), refreshTokenValue);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenValue)
                .build();
    }

    @Transactional
    public ReissueTokenResponse reissueToken(ReissueTokenRequest request) {
        Claims accessClaims = jwtUtil.getClaimsFromExpiredToken(request.accessToken());
        Long accessUserId = accessClaims.get("userId", Long.class);

        if (accessUserId == null || !jwtUtil.validateToken(request.refreshToken())) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN);
        }

        String lockKey = getReissueLockKey(accessUserId);
        String lockOwner = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                lockOwner,
                REISSUE_LOCK_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
        if (!Boolean.TRUE.equals(locked)) {
            throw new GeneralException(
                    GeneralErrorCode.SERVICE_UNAVAILABLE,
                    "토큰 재발급 요청이 처리 중입니다. 잠시 후 다시 시도해주세요."
            );
        }

        try {
            String storedRefreshToken = redisTemplate.opsForValue().get(getRefreshTokenKey(accessUserId));
            if (storedRefreshToken == null || !storedRefreshToken.equals(request.refreshToken())) {
                throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "토큰 정보가 일치하지 않습니다.");
            }

            User user = userRepository.findById(accessUserId)
                    .orElseThrow(() -> new GeneralException(GeneralErrorCode.USER_NOT_FOUND));

            String newAccessToken = jwtUtil.createAccessToken(user.getEmail(), user.getId());
            String newRefreshToken = jwtUtil.createRefreshToken(user.getEmail());

            saveRefreshToken(user.getId(), newRefreshToken);

            return ReissueTokenResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(newRefreshToken)
                    .build();
        } finally {
            releaseReissueLock(lockKey, lockOwner);
        }
    }

    @Transactional
    public void logout(LogoutRequest request) {
        String accessToken = request.accessToken();
        String refreshToken = request.refreshToken();

        Claims claims = extractLogoutClaims(accessToken);
        Long userId = claims.get("userId", Long.class);
        if (userId == null) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN);
        }

        String storedRefreshToken = redisTemplate.opsForValue().get(getRefreshTokenKey(userId));
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new GeneralException(GeneralErrorCode.INVALID_TOKEN, "토큰 정보가 일치하지 않습니다.");
        }

        redisTemplate.delete(getRefreshTokenKey(userId));

        long remainingTime = jwtUtil.getRemainingTime(accessToken);
        if (remainingTime > 0) {
            redisTemplate.opsForValue().set(
                    getBlacklistKey(accessToken),
                    "logout",
                    remainingTime,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private Claims extractLogoutClaims(String accessToken) {
        try {
            return jwtUtil.getClaimsFromToken(accessToken);
        } catch (GeneralException exception) {
            return jwtUtil.getClaimsFromExpiredToken(accessToken);
        }
    }

    private void saveRefreshToken(Long userId, String refreshTokenValue) {
        redisTemplate.opsForValue().set(
                getRefreshTokenKey(userId),
                refreshTokenValue,
                jwtUtil.getRefreshTokenTime(),
                TimeUnit.MILLISECONDS
        );
    }

    private String getRefreshTokenKey(Long userId) {
        return REFRESH_TOKEN_PREFIX + userId;
    }

    private String getBlacklistKey(String accessToken) {
        return BLACKLIST_PREFIX + accessToken;
    }

    private String getReissueLockKey(Long userId) {
        return REISSUE_LOCK_PREFIX + userId;
    }

    private void releaseReissueLock(String lockKey, String lockOwner) {
        redisTemplate.execute(
                COMPARE_AND_DELETE_SCRIPT,
                Collections.singletonList(lockKey),
                lockOwner
        );
    }

    private static DefaultRedisScript<Long> createCompareAndDeleteScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) " +
                        "else return 0 end"
        );
        return script;
    }
}
