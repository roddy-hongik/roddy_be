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
import com.roddy.global.config.s3.S3ObjectUrlService;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class MyPageControllerTest {

    private static final String PROFILE_IMAGE_URL = "https://s3.example.com/profile-image.png?signed";

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

    /** 포트폴리오와 프로필 이미지 주소는 볼 때마다 S3 에서 새로 만든다. 테스트에서는 실제로 서명하지 않는다. */
    @MockitoBean
    private S3ObjectUrlService s3ObjectUrlService;

    /** 올린 이미지가 있는지 S3 에 물어본다. 테스트에서는 실제로 부르지 않는다. */
    @MockitoBean
    private S3Client s3Client;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        given(s3ObjectUrlService.createPresignedGetUrl(anyString()))
                .willReturn("https://cdn.example.com/portfolio.pdf?signed");

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
                .andExpect(jsonPath("$.result.profileImageUrl").doesNotExist())
                .andExpect(jsonPath("$.result.desiredJob").value("BACKEND"))
                .andExpect(jsonPath("$.result.desiredCompany").value("네이버"))
                .andExpect(jsonPath("$.result.experienceYears").value("JUNIOR"))
                .andExpect(jsonPath("$.result.portfolioFileName").value("portfolio.pdf"))
                .andExpect(jsonPath("$.result.githubConnected").value(false));
    }

    @Test
    void 내_프로필_수정_성공() throws Exception {
        User user = saveOnboardedUser("update-mypage@example.com", "수정전");

        updateProfile(user, new UpdateProfileRequest("수정후", 31, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("수정후"))
                .andExpect(jsonPath("$.result.age").value(31));
    }

    @Test
    void 내_프로필_수정시_이름이_blank이면_실패() throws Exception {
        User user = saveOnboardedUser("invalid-mypage@example.com", "검증");

        updateProfile(user, new UpdateProfileRequest(" ", 31, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 프로필_이미지_업로드_URL을_발급한다() throws Exception {
        User user = saveOnboardedUser("presign-image@example.com", "이미지");
        given(s3ObjectUrlService.createPresignedPutUrl(anyString(), eq("image/png"), anyLong()))
                .willReturn("https://s3.example.com/upload?signed");

        mockMvc.perform(post("/api/mypage/profile-image/presign")
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileName", "me.PNG"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.uploadUrl").value("https://s3.example.com/upload?signed"))
                // 남의 이미지를 프로필로 걸지 못하게 키에 사용자 id 를 넣는다.
                .andExpect(jsonPath("$.result.objectKey").value(startsWith("profile-image/" + user.getId() + "/")))
                .andExpect(jsonPath("$.result.objectKey").value(endsWith(".png")))
                .andExpect(jsonPath("$.result.contentType").value("image/png"));
    }

    @Test
    void 프로필_이미지는_png와_jpg만_올릴_수_있다() throws Exception {
        User user = saveOnboardedUser("gif-image@example.com", "움짤");

        mockMvc.perform(post("/api/mypage/profile-image/presign")
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fileName", "me.gif"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 올린_이미지의_키로_프로필_이미지를_바꾼다() throws Exception {
        User user = saveOnboardedUser("change-image@example.com", "이미지변경");
        String objectKey = "profile-image/" + user.getId() + "/new.png";
        givenUploadedImage(1024L);
        given(s3ObjectUrlService.createPresignedGetUrl(objectKey)).willReturn(PROFILE_IMAGE_URL);

        // 키가 아니라 볼 때마다 새로 만든 주소를 준다.
        updateProfile(user, new UpdateProfileRequest("이미지변경", 27, objectKey, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.profileImageUrl").value(PROFILE_IMAGE_URL));

        mockMvc.perform(get("/api/mypage/profile")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.profileImageUrl").value(PROFILE_IMAGE_URL));
    }

    @Test
    void 내_경로가_아닌_키로는_프로필_이미지를_바꿀_수_없다() throws Exception {
        User user = saveOnboardedUser("others-key@example.com", "남의키");

        updateProfile(user, new UpdateProfileRequest("남의키", 27, "profile-image/999999/other.png", null))
                .andExpect(status().isBadRequest());
        // 내 포트폴리오라도 프로필 이미지로는 걸 수 없다.
        updateProfile(user, new UpdateProfileRequest("남의키", 27, "portfolio/" + user.getId() + "/me.pdf", null))
                .andExpect(status().isBadRequest());

        verify(s3Client, never()).headObject(any(HeadObjectRequest.class));
    }

    @Test
    void 올리지_않은_키로는_프로필_이미지를_바꿀_수_없다() throws Exception {
        User user = saveOnboardedUser("not-uploaded@example.com", "안올림");
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willThrow(NoSuchKeyException.builder().statusCode(404).message("Not Found").build());

        updateProfile(user, new UpdateProfileRequest("안올림", 27, "profile-image/" + user.getId() + "/missing.png", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 너무_큰_이미지는_프로필로_걸_수_없다() throws Exception {
        User user = saveOnboardedUser("too-large@example.com", "큰이미지");
        givenUploadedImage(6L * 1024 * 1024);

        updateProfile(user, new UpdateProfileRequest("큰이미지", 27, "profile-image/" + user.getId() + "/large.png", null))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 프로필_이미지를_지운다() throws Exception {
        User user = saveOnboardedUserWithImage("remove-image@example.com", "지우기");

        updateProfile(user, new UpdateProfileRequest("지우기", 27, null, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.profileImageUrl").doesNotExist());
    }

    @Test
    void 이미지를_건드리지_않으면_지금_이미지를_그대로_둔다() throws Exception {
        User user = saveOnboardedUserWithImage("keep-image@example.com", "그대로");

        updateProfile(user, new UpdateProfileRequest("이름만변경", 27, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("이름만변경"))
                .andExpect(jsonPath("$.result.profileImageUrl").value(PROFILE_IMAGE_URL));
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

    private ResultActions updateProfile(User user, UpdateProfileRequest request) throws Exception {
        return mockMvc.perform(patch("/api/mypage/profile")
                .with(user(new UserDetailsImpl(user)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private void givenUploadedImage(long contentLength) {
        given(s3Client.headObject(any(HeadObjectRequest.class)))
                .willReturn(HeadObjectResponse.builder().contentLength(contentLength).build());
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
                "portfolio/1/portfolio.pdf",
                "portfolio.pdf",
                LocalDateTime.now()
        );
        return userRepository.save(user);
    }

    private User saveOnboardedUserWithImage(String email, String name) {
        User user = saveOnboardedUser(email, name);
        String objectKey = "profile-image/" + user.getId() + "/saved.png";
        user.changeProfileImage(objectKey);
        given(s3ObjectUrlService.createPresignedGetUrl(objectKey)).willReturn(PROFILE_IMAGE_URL);
        return userRepository.save(user);
    }

    private record UpdateProfileRequest(
            String name,
            Integer age,
            String profileImageObjectKey,
            Boolean removeProfileImage
    ) {
    }
}
