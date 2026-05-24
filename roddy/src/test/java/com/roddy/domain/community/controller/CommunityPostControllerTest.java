package com.roddy.domain.community.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.community.entity.CommunityPost;
import com.roddy.domain.community.enums.CommunityTag;
import com.roddy.domain.community.repository.CommunityCommentRepository;
import com.roddy.domain.community.repository.CommunityPostImageRepository;
import com.roddy.domain.community.repository.CommunityPostLikeRepository;
import com.roddy.domain.community.repository.CommunityPostReportRepository;
import com.roddy.domain.community.repository.CommunityPostRepository;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.global.config.s3.S3Uploader;
import com.roddy.global.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class CommunityPostControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CommunityPostRepository communityPostRepository;

    @Autowired
    private CommunityPostLikeRepository communityPostLikeRepository;

    @Autowired
    private CommunityPostReportRepository communityPostReportRepository;

    @Autowired
    private CommunityCommentRepository communityCommentRepository;

    @Autowired
    private CommunityPostImageRepository communityPostImageRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private S3Uploader s3Uploader;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        communityPostLikeRepository.deleteAll();
        communityPostReportRepository.deleteAll();
        communityCommentRepository.deleteAll();
        communityPostImageRepository.deleteAll();
        communityPostRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 게시글_작성_성공() throws Exception {
        User user = saveUser("writer@example.com", "작성자");
        given(s3Uploader.upload(any(), anyString())).willReturn("https://s3.amazonaws.com/test/community/" + UUID.randomUUID() + ".png");

        MockMultipartFile image = new MockMultipartFile(
                "images",
                "test.png",
                MediaType.IMAGE_PNG_VALUE,
                "image".getBytes()
        );

        mockMvc.perform(multipart("/api/community/posts")
                        .file(image)
                        .param("tag", "B2C")
                        .param("title", "첫 글")
                        .param("content", "본문입니다.")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").isNumber());
    }

    @Test
    void 제목이_비어있으면_실패() throws Exception {
        User user = saveUser("writer2@example.com", "작성자2");

        mockMvc.perform(multipart("/api/community/posts")
                        .param("tag", "B2C")
                        .param("title", " ")
                        .param("content", "본문입니다.")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 게시글_목록_조회_성공() throws Exception {
        User user = saveUser("list@example.com", "목록작성자");
        savePost(user, CommunityTag.B2C, "첫 번째 글");
        savePost(user, CommunityTag.B2B, "두 번째 글");

        mockMvc.perform(get("/api/community/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.posts.length()").value(2));
    }

    @Test
    void 태그별_목록_조회_성공() throws Exception {
        User user = saveUser("tag@example.com", "태그작성자");
        savePost(user, CommunityTag.B2C, "B2C 글");
        savePost(user, CommunityTag.FINTECH, "핀테크 글");

        mockMvc.perform(get("/api/community/posts").param("tag", "FINTECH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.posts.length()").value(1))
                .andExpect(jsonPath("$.result.posts[0].tag").value("FINTECH"));
    }

    @Test
    void 상세_조회시_조회수가_증가한다() throws Exception {
        User user = saveUser("detail@example.com", "상세작성자");
        CommunityPost post = savePost(user, CommunityTag.FINTECH, "상세 글");

        mockMvc.perform(get("/api/community/posts/{postId}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.viewCount").value(1));
    }

    @Test
    void 좋아요_추가() throws Exception {
        User user = saveUser("like@example.com", "좋아요사용자");
        CommunityPost post = savePost(user, CommunityTag.B2C, "좋아요 글");

        mockMvc.perform(post("/api/community/posts/{postId}/like", post.getId())
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.liked").value(true))
                .andExpect(jsonPath("$.result.likeCount").value(1));
    }

    @Test
    void 좋아요_취소() throws Exception {
        User user = saveUser("unlike@example.com", "좋아요취소사용자");
        CommunityPost post = savePost(user, CommunityTag.B2C, "좋아요 취소 글");

        mockMvc.perform(post("/api/community/posts/{postId}/like", post.getId())
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/community/posts/{postId}/like", post.getId())
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.liked").value(false))
                .andExpect(jsonPath("$.result.likeCount").value(0));
    }

    @Test
    void 중복_신고_방지() throws Exception {
        User user = saveUser("report@example.com", "신고사용자");
        CommunityPost post = savePost(user, CommunityTag.GENERALIST, "신고 글");

        mockMvc.perform(post("/api/community/posts/{postId}/report", post.getId())
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.reported").value(true));

        mockMvc.perform(post("/api/community/posts/{postId}/report", post.getId())
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 댓글_작성_성공() throws Exception {
        User user = saveUser("comment@example.com", "댓글사용자");
        CommunityPost post = savePost(user, CommunityTag.B2B, "댓글 글");

        mockMvc.perform(post("/api/community/posts/{postId}/comments", post.getId())
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("댓글 내용"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content").value("댓글 내용"))
                .andExpect(jsonPath("$.result.authorName").value("댓글사용자"));
    }

    @Test
    void 비로그인_사용자는_댓글_작성에_실패한다() throws Exception {
        User user = saveUser("anonymous@example.com", "익명대상");
        CommunityPost post = savePost(user, CommunityTag.INFRA_DEVOPS, "인증 글");

        mockMvc.perform(post("/api/community/posts/{postId}/comments", post.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("댓글 내용"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    private User saveUser(String email, String nickname) {
        return userRepository.save(
                User.builder()
                        .email(email)
                        .password("encoded-password")
                        .socialType(SocialType.LOCAL)
                        .nickname(nickname)
                        .username(nickname)
                        .role(Role.USER)
                        .build()
        );
    }

    private CommunityPost savePost(User author, CommunityTag tag, String title) {
        return communityPostRepository.save(CommunityPost.create(author, tag, title, title + " 본문"));
    }

    private record CommentRequest(String content) {
    }
}
