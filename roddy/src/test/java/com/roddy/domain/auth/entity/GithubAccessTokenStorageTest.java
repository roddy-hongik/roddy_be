package com.roddy.domain.auth.entity;

import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.global.config.s3.S3Uploader;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class GithubAccessTokenStorageTest {

    private static final String ACCESS_TOKEN = "gho_exampleGithubAccessToken1234567890";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private S3Uploader s3Uploader;

    @MockitoBean
    private SocialAuthService socialAuthService;

    private final List<User> createdUsers = new ArrayList<>();

    @AfterEach
    void tearDown() {
        userRepository.deleteAll(createdUsers);
        createdUsers.clear();
    }

    @Test
    @DisplayName("깃허브 토큰은 암호화되어 저장되고 읽을 때 원래대로 돌아온다")
    void storesTokenEncrypted() {
        User user = saveUser("token@example.com");

        transactionTemplate.executeWithoutResult(status -> {
            User found = userRepository.findById(user.getId()).orElseThrow();
            found.connectGithub("12345", "https://github.com/octocat", ACCESS_TOKEN);
        });

        // 애플리케이션에서는 평문으로 읽힌다.
        assertThat(userRepository.findById(user.getId()).orElseThrow().getGithubAccessToken())
                .isEqualTo(ACCESS_TOKEN);

        // DB 에는 평문이 남아 있으면 안 된다.
        assertThat(rawToken(user.getId()))
                .isNotNull()
                .isNotEqualTo(ACCESS_TOKEN)
                .doesNotContain("gho_");
    }

    @Test
    @DisplayName("깃허브 연결을 끊으면 토큰을 지운다")
    void clearsTokenOnDisconnect() {
        User user = saveUser("disconnect@example.com");

        transactionTemplate.executeWithoutResult(status -> {
            User found = userRepository.findById(user.getId()).orElseThrow();
            found.connectGithub("12345", "https://github.com/octocat", ACCESS_TOKEN);
        });
        transactionTemplate.executeWithoutResult(status ->
                userRepository.findById(user.getId()).orElseThrow().disconnectGithub());

        User found = userRepository.findById(user.getId()).orElseThrow();
        assertThat(found.getGithubAccessToken()).isNull();
        assertThat(found.isGithubConnected()).isFalse();
        assertThat(found.getGithubId()).isNull();
    }

    @Test
    @DisplayName("탈퇴하면 토큰을 지운다")
    void clearsTokenOnWithdraw() {
        User user = saveUser("withdraw@example.com");

        transactionTemplate.executeWithoutResult(status -> {
            User found = userRepository.findById(user.getId()).orElseThrow();
            found.connectGithub("12345", "https://github.com/octocat", ACCESS_TOKEN);
        });
        transactionTemplate.executeWithoutResult(status ->
                userRepository.findById(user.getId()).orElseThrow().withdraw());

        assertThat(rawToken(user.getId())).isNull();
    }

    private String rawToken(Long userId) {
        return transactionTemplate.execute(status -> (String) entityManager
                .createNativeQuery("select github_access_token from users where user_id = :userId")
                .setParameter("userId", userId)
                .getSingleResult());
    }

    private User saveUser(String email) {
        User saved = userRepository.save(
                User.builder()
                        .email(email)
                        .password("encoded-password")
                        .socialType(SocialType.LOCAL)
                        .nickname("토큰유저")
                        .username("토큰유저")
                        .role(Role.USER)
                        .build()
        );
        createdUsers.add(saved);
        return saved;
    }
}
