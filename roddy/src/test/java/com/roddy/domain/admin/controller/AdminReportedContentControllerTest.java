package com.roddy.domain.admin.controller;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.community.entity.CommunityComment;
import com.roddy.domain.community.entity.CommunityCommentReport;
import com.roddy.domain.community.entity.CommunityPost;
import com.roddy.domain.community.entity.CommunityPostLike;
import com.roddy.domain.community.entity.CommunityPostReport;
import com.roddy.domain.community.enums.CommunityJobCategory;
import com.roddy.domain.community.enums.CommunityPostCategory;
import com.roddy.domain.community.repository.CommunityCommentReportRepository;
import com.roddy.domain.community.repository.CommunityCommentRepository;
import com.roddy.domain.community.repository.CommunityPostLikeRepository;
import com.roddy.domain.community.repository.CommunityPostReportRepository;
import com.roddy.domain.community.repository.CommunityPostRepository;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.global.config.s3.S3Uploader;
import com.roddy.global.security.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AdminReportedContentControllerTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private CommunityPostRepository communityPostRepository;
    @Autowired private CommunityCommentRepository communityCommentRepository;
    @Autowired private CommunityPostReportRepository communityPostReportRepository;
    @Autowired private CommunityCommentReportRepository communityCommentReportRepository;
    @Autowired private CommunityPostLikeRepository communityPostLikeRepository;

    @MockitoBean private S3Uploader s3Uploader;
    @MockitoBean private SocialAuthService socialAuthService;

    private MockMvc mockMvc;
    private final List<User> users = new ArrayList<>();
    private User admin;
    private User writer;
    private User reporter;
    private User anotherReporter;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        admin = saveUser("admin-contents@example.com", Role.ADMIN);
        writer = saveUser("content-writer@example.com", Role.USER);
        reporter = saveUser("content-reporter@example.com", Role.USER);
        anotherReporter = saveUser("content-reporter2@example.com", Role.USER);
    }

    @AfterEach
    void tearDown() {
        communityPostLikeRepository.deleteAll();
        communityCommentReportRepository.deleteAll();
        communityPostReportRepository.deleteAll();
        communityCommentRepository.deleteAll();
        communityPostRepository.deleteAll();
        userRepository.deleteAll(users);
        users.clear();
    }

    @Test
    void 신고된_글과_댓글을_신고_많은_순으로_보여준다() throws Exception {
        CommunityPost reportedPost = savePost("신고 두 번 받은 글");
        reportPost(reportedPost, reporter);
        reportPost(reportedPost, anotherReporter);
        savePost("신고 없는 글");
        CommunityComment comment = saveComment(reportedPost, "신고 세 번 받은 댓글", null);
        User thirdReporter = saveUser("content-reporter3@example.com", Role.USER);
        reportComment(comment, reporter);
        reportComment(comment, anotherReporter);
        reportComment(comment, thirdReporter);

        mockMvc.perform(get("/api/admin/reported-contents").with(user(new UserDetailsImpl(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].type").value("comment"))
                .andExpect(jsonPath("$.result[0].id").value(comment.getId()))
                .andExpect(jsonPath("$.result[0].reportCount").value(3))
                .andExpect(jsonPath("$.result[0].author").value("content-writer"))
                .andExpect(jsonPath("$.result[1].type").value("post"))
                .andExpect(jsonPath("$.result[1].reportCount").value(2))
                .andExpect(jsonPath("$.result[1].contentPreview").value("신고 두 번 받은 글"))
                .andExpect(jsonPath("$.result[1].status").value("reported"));
    }

    @Test
    void 신고된_글을_지우면_댓글과_좋아요와_신고도_함께_지운다() throws Exception {
        CommunityPost post = savePost("지울 글");
        reportPost(post, reporter);
        CommunityComment root = saveComment(post, "댓글", null);
        CommunityComment reply = saveComment(post, "대댓글", root);
        reportComment(reply, reporter);
        communityPostLikeRepository.save(CommunityPostLike.create(post, anotherReporter));
        CommunityPost otherPost = savePost("남을 글");

        mockMvc.perform(delete("/api/admin/reported-contents/posts/{postId}", post.getId())
                        .param("reason", "스팸")
                        .with(user(new UserDetailsImpl(admin))))
                .andExpect(status().isOk());

        assertThat(communityPostRepository.findById(post.getId())).isEmpty();
        assertThat(communityPostRepository.findById(otherPost.getId())).isPresent();
        assertThat(communityCommentRepository.countByPost_Id(post.getId())).isZero();
        assertThat(communityPostReportRepository.count()).isZero();
        assertThat(communityCommentReportRepository.count()).isZero();
        assertThat(communityPostLikeRepository.count()).isZero();
    }

    @Test
    void 신고된_댓글을_지우면_대댓글과_신고도_함께_지운다() throws Exception {
        CommunityPost post = savePost("댓글이 지워질 글");
        CommunityComment root = saveComment(post, "신고된 댓글", null);
        CommunityComment reply = saveComment(post, "대댓글", root);
        CommunityComment otherComment = saveComment(post, "남을 댓글", null);
        reportComment(root, reporter);
        reportComment(reply, anotherReporter);

        mockMvc.perform(delete("/api/admin/reported-contents/comments/{commentId}", root.getId())
                        .with(user(new UserDetailsImpl(admin))))
                .andExpect(status().isOk());

        assertThat(communityCommentRepository.findById(root.getId())).isEmpty();
        assertThat(communityCommentRepository.findById(reply.getId())).isEmpty();
        assertThat(communityCommentRepository.findById(otherComment.getId())).isPresent();
        assertThat(communityCommentReportRepository.count()).isZero();
        assertThat(communityPostRepository.findById(post.getId())).isPresent();
    }

    @Test
    void 없는_글은_지울_수_없다() throws Exception {
        mockMvc.perform(delete("/api/admin/reported-contents/posts/{postId}", 999_999L)
                        .with(user(new UserDetailsImpl(admin))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMUNITY_4041"));
    }

    @Test
    void 어드민이_아니면_신고_콘텐츠를_관리할_수_없다() throws Exception {
        CommunityPost post = savePost("권한 확인 글");

        mockMvc.perform(get("/api/admin/reported-contents").with(user(new UserDetailsImpl(writer))))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/reported-contents/posts/{postId}", post.getId())
                        .with(user(new UserDetailsImpl(writer))))
                .andExpect(status().isForbidden());
        assertThat(communityPostRepository.findById(post.getId())).isPresent();
    }

    private CommunityPost savePost(String title) {
        return communityPostRepository.save(CommunityPost.create(
                writer, CommunityPostCategory.FREE, CommunityJobCategory.B2B, title, title + " 본문", List.of()));
    }

    private CommunityComment saveComment(CommunityPost post, String content, CommunityComment parent) {
        return communityCommentRepository.save(CommunityComment.create(post, writer, content, parent));
    }

    private void reportPost(CommunityPost post, User user) {
        communityPostReportRepository.save(CommunityPostReport.create(post, user, null));
        post.increaseReportCount();
        communityPostRepository.save(post);
    }

    private void reportComment(CommunityComment comment, User user) {
        communityCommentReportRepository.save(CommunityCommentReport.create(comment, user, null));
    }

    private User saveUser(String email, Role role) {
        User saved = userRepository.save(User.builder()
                .email(email)
                .password("encoded-password")
                .socialType(SocialType.LOCAL)
                .nickname(email.substring(0, email.indexOf('@')))
                .username(email.substring(0, email.indexOf('@')))
                .role(role)
                .build());
        users.add(saved);
        return saved;
    }
}
