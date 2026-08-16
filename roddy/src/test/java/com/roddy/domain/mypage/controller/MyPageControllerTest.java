package com.roddy.domain.mypage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.enums.ExperienceLevel;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.domain.mypage.entity.DesiredCompany;
import com.roddy.domain.mypage.repository.DesiredCompanyRepository;
import com.roddy.global.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class MyPageControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DesiredCompanyRepository desiredCompanyRepository;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private SocialAuthService socialAuthService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        desiredCompanyRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 내_프로필_조회_성공() throws Exception {
        User user = saveOnboardedUser("mypage@example.com", "기존이름");
        desiredCompanyRepository.save(DesiredCompany.create(user, DesiredJob.BACKEND, "네이버"));

        mockMvc.perform(get("/api/mypage/profile")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("기존이름"))
                .andExpect(jsonPath("$.result.age").value(27))
                .andExpect(jsonPath("$.result.desiredJob").value("BACKEND"))
                .andExpect(jsonPath("$.result.desiredCompany").value("네이버"))
                .andExpect(jsonPath("$.result.experienceYears").value("JUNIOR"))
                .andExpect(jsonPath("$.result.portfolioFileName").value("portfolio.pdf"))
                .andExpect(jsonPath("$.result.githubConnected").value(false));
    }

    @Test
    void 내_프로필_수정_성공() throws Exception {
        User user = saveOnboardedUser("update-mypage@example.com", "수정전");

        mockMvc.perform(patch("/api/mypage/profile")
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest(
                                "수정후",
                                31,
                                "https://cdn.example.com/profile.png"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("수정후"))
                .andExpect(jsonPath("$.result.age").value(31))
                .andExpect(jsonPath("$.result.profileImageUrl").value("https://cdn.example.com/profile.png"));
    }

    @Test
    void 내_프로필_수정시_이름이_blank이면_실패() throws Exception {
        User user = saveOnboardedUser("invalid-mypage@example.com", "검증");

        mockMvc.perform(patch("/api/mypage/profile")
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest(
                                " ",
                                31,
                                null
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 회원_탈퇴_성공() throws Exception {
        User user = saveOnboardedUser("withdraw-mypage@example.com", "탈퇴");

        mockMvc.perform(delete("/api/mypage/me")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

        User withdrawnUser = userRepository.findById(user.getId()).orElseThrow();
        verify(redisTemplate).delete("RefreshToken:" + user.getId());
        assertNotNull(withdrawnUser.getDeletedAt());
    }

    private User saveOnboardedUser(String email, String name) {
        User user = User.builder()
                .email(email)
                .password("password")
                .socialType(SocialType.LOCAL)
                .nickname(name)
                .username(name)
                .role(Role.USER)
                .build();
        user.completeProfile(
                name,
                27,
                ExperienceLevel.JUNIOR,
                DesiredJob.BACKEND,
                "https://cdn.example.com/portfolio.pdf",
                "portfolio.pdf",
                LocalDateTime.now()
        );
        return userRepository.save(user);
    }

    private record UpdateProfileRequest(
            String name,
            Integer age,
            String profileImageUrl
    ) {
    }
}
