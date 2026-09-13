package com.roddy.domain.auth.controller;

import com.roddy.domain.auth.dto.response.SocialLoginResponse;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.AuthService;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuthControllerTest {

    private static final String PASSWORD = "Password1!";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthService authService;

    /** 리프레시 토큰을 레디스에 저장한다. 테스트에서는 저장했다고 치고 넘어간다. */
    @MockitoBean
    private StringRedisTemplate redisTemplate;

    /** 카카오/구글 사용자 정보는 외부 API 로 받아온다. 테스트에서는 그 결과만 흉내 낸다. */
    @MockitoBean
    private SocialAuthService socialAuthService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        given(redisTemplate.opsForValue()).willReturn(mock(ValueOperations.class));
    }

    @Test
    void 로컬_로그인_응답에_권한이_담긴다() throws Exception {
        saveLocalUser("admin-login@example.com", Role.ADMIN);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "admin-login@example.com", "password": "%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.result.role").value("ADMIN"));
    }

    @Test
    void 소셜_로그인_응답에_권한이_담긴다() throws Exception {
        User user = saveLocalUser("user-kakao@example.com", Role.USER);
        // issueTokens 도 레디스 목을 부르므로, 다른 목을 스텁하는 도중에 부르면 안 된다.
        SocialLoginResponse response = SocialLoginResponse.from(authService.issueTokens(user), user);
        given(socialAuthService.loginWithKakao(anyString())).willReturn(response);

        mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accessToken": "kakao-access-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.role").value("USER"))
                .andExpect(jsonPath("$.result.user.email").value("user-kakao@example.com"));
    }

    private User saveLocalUser(String email, Role role) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(PASSWORD))
                .socialType(SocialType.LOCAL)
                .nickname("로디")
                .username("로디")
                .role(role)
                .build());
    }
}
