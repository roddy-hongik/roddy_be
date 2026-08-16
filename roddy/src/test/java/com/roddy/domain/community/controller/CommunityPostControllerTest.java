package com.roddy.domain.community.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.community.entity.CommunityInterviewPostDetail;
import com.roddy.domain.community.entity.CommunityPost;
import com.roddy.domain.community.entity.CommunityRoadmapPostDetail;
import com.roddy.domain.community.enums.CommunityJobCategory;
import com.roddy.domain.community.enums.CommunityInterviewSubtype;
import com.roddy.domain.community.enums.CommunityPostCategory;
import com.roddy.domain.community.repository.CommunityCommentRepository;
import com.roddy.domain.community.repository.CommunityCommentReportRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
    private CommunityCommentReportRepository communityCommentReportRepository;

    @Autowired
    private CommunityPostImageRepository communityPostImageRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private S3Uploader s3Uploader;

    @MockitoBean
    private SocialAuthService socialAuthService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        communityPostLikeRepository.deleteAll();
        communityPostReportRepository.deleteAll();
        communityCommentReportRepository.deleteAll();
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
                        .param("postCategory", "ROADMAP")
                        .param("jobCategory", "B2C")
                        .param("title", "첫 글")
                        .param("content", "본문입니다.")
                        .param("company", "토스")
                        .param("position", "백엔드")
                        .param("techStacks", "Java", "Spring")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").isNumber());
    }

    @Test
    void 인터뷰_게시글_작성_성공() throws Exception {
        User user = saveUser("interview-writer@example.com", "인터뷰작성자");

        mockMvc.perform(multipart("/api/community/posts")
                        .param("postCategory", "PASS_REVIEW_INTERVIEW")
                        .param("jobCategory", "FINTECH")
                        .param("title", "토스 백엔드 합격 후기")
                        .param("content", "전체 후기 본문")
                        .param("company", "토스")
                        .param("jobRole", "백엔드")
                        .param("preparationPeriod", "3개월")
                        .param("techStacks", "Java", "Spring")
                        .param("processSummary", "서류-과제-면접")
                        .param("background", "백엔드 전환 준비")
                        .param("preparationProcess", "CS와 프로젝트 정리")
                        .param("experienceDetail", "면접에서 트랜잭션 질문을 받음")
                        .param("advice", "프로젝트 선택 근거를 분명히 준비")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").isNumber());
    }

    @Test
    void 인터뷰_게시글은_필수_상세값이_없으면_실패한다() throws Exception {
        User user = saveUser("interview-invalid@example.com", "인터뷰검증작성자");

        mockMvc.perform(multipart("/api/community/posts")
                        .param("postCategory", "PASS_REVIEW_INTERVIEW")
                        .param("jobCategory", "FINTECH")
                        .param("title", "필수값 없는 인터뷰 글")
                        .param("content", "본문")
                        .param("company", "토스")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 제목이_비어있으면_실패() throws Exception {
        User user = saveUser("writer2@example.com", "작성자2");

        mockMvc.perform(multipart("/api/community/posts")
                        .param("postCategory", "FREE")
                        .param("jobCategory", "B2C")
                        .param("title", " ")
                        .param("content", "본문입니다.")
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 게시글_목록_조회_성공() throws Exception {
        User user = saveUser("list@example.com", "목록작성자");
        savePost(user, CommunityPostCategory.FREE, CommunityJobCategory.B2C, "첫 번째 글", "카카오", "백엔드", "Spring");
        savePost(user, CommunityPostCategory.ROADMAP, CommunityJobCategory.B2B, "두 번째 글", "네이버", "프론트엔드", "React");

        mockMvc.perform(get("/api/community/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.posts.length()").value(2))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20));
    }

    @Test
    void postCategory만으로_목록_필터링_성공() throws Exception {
        User user = saveUser("tag@example.com", "태그작성자");
        savePost(user, CommunityPostCategory.FREE, CommunityJobCategory.B2C, "B2C 글", "카카오", "백엔드", "Spring");
        savePost(user, CommunityPostCategory.ROADMAP, CommunityJobCategory.FINTECH, "핀테크 글", "토스", "백엔드", "Java");

        mockMvc.perform(get("/api/community/posts").param("postCategory", "ROADMAP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.posts.length()").value(1))
                .andExpect(jsonPath("$.result.posts[0].postCategory").value("ROADMAP"));
    }

    @Test
    void jobCategory만으로_목록_필터링_성공() throws Exception {
        User user = saveUser("job@example.com", "직무작성자");
        savePost(user, CommunityPostCategory.FREE, CommunityJobCategory.B2C, "B2C 글", "카카오", "백엔드", "Spring");
        savePost(user, CommunityPostCategory.FREE, CommunityJobCategory.FINTECH, "핀테크 글", "토스", "백엔드", "Java");

        mockMvc.perform(get("/api/community/posts").param("jobCategory", "FINTECH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.posts.length()").value(1))
                .andExpect(jsonPath("$.result.posts[0].jobCategory").value("FINTECH"));
    }

    @Test
    void postCategory와_jobCategory_조합_필터링_성공() throws Exception {
        User user = saveUser("combo@example.com", "조합작성자");
        savePost(user, CommunityPostCategory.ROADMAP, CommunityJobCategory.FINTECH, "일치 글", "토스", "백엔드", "Spring");
        savePost(user, CommunityPostCategory.ROADMAP, CommunityJobCategory.B2C, "불일치 글", "카카오", "백엔드", "Spring");

        mockMvc.perform(get("/api/community/posts")
                        .param("postCategory", "ROADMAP")
                        .param("jobCategory", "FINTECH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.posts.length()").value(1))
                .andExpect(jsonPath("$.result.posts[0].title").value("일치 글"));
    }

    @Test
    void keyword로_title_검색_성공() throws Exception {
        User user = saveUser("keyword-title@example.com", "검색작성자");
        savePost(user, CommunityPostCategory.FREE, CommunityJobCategory.B2C, "스프링 백엔드 질문", "카카오", "백엔드", "Java");
        savePost(user, CommunityPostCategory.FREE, CommunityJobCategory.B2C, "리액트 질문", "카카오", "프론트엔드", "React");

        mockMvc.perform(get("/api/community/posts").param("keyword", "스프링"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.posts.length()").value(1))
                .andExpect(jsonPath("$.result.posts[0].title").value("스프링 백엔드 질문"));
    }

    @Test
    void keyword로_company_검색_성공() throws Exception {
        User user = saveUser("keyword-company@example.com", "회사검색작성자");
        savePost(user, CommunityPostCategory.ROADMAP, CommunityJobCategory.B2C, "질문", "카카오", "백엔드", "Java");
        savePost(user, CommunityPostCategory.ROADMAP, CommunityJobCategory.B2C, "다른 질문", "네이버", "백엔드", "Java");

        mockMvc.perform(get("/api/community/posts").param("keyword", "카카오"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.posts.length()").value(1))
                .andExpect(jsonPath("$.result.posts[0].company").value("카카오"));
    }

    @Test
    void keyword로_position_검색_성공() throws Exception {
        User user = saveUser("keyword-position@example.com", "직무검색작성자");
        savePost(user, CommunityPostCategory.PASS_REVIEW_INTERVIEW, CommunityJobCategory.B2C, "질문", "카카오", "데이터 엔지니어", "Python");
        savePost(user, CommunityPostCategory.PASS_REVIEW_INTERVIEW, CommunityJobCategory.B2C, "다른 질문", "카카오", "백엔드", "Java");

        mockMvc.perform(get("/api/community/posts").param("keyword", "데이터"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.posts.length()").value(1))
                .andExpect(jsonPath("$.result.posts[0].position").value("데이터 엔지니어"));
    }

    @Test
    void keyword로_techStacks_검색_성공() throws Exception {
        User user = saveUser("keyword-tech@example.com", "스택검색작성자");
        savePost(user, CommunityPostCategory.ROADMAP, CommunityJobCategory.B2C, "질문", "카카오", "백엔드", "Spring");
        savePost(user, CommunityPostCategory.PASS_REVIEW_INTERVIEW, CommunityJobCategory.B2C, "다른 질문", "카카오", "백엔드", "React");

        mockMvc.perform(get("/api/community/posts").param("keyword", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.posts.length()").value(1))
                .andExpect(jsonPath("$.result.posts[0].techStacks[0]").value("Spring"));
    }

    @Test
    void company_필터_성공() throws Exception {
        User user = saveUser("company-filter@example.com", "회사필터작성자");
        savePost(user, CommunityPostCategory.ROADMAP, CommunityJobCategory.B2C, "질문", "카카오", "백엔드", "Spring");
        savePost(user, CommunityPostCategory.ROADMAP, CommunityJobCategory.B2C, "다른 질문", "토스", "백엔드", "Spring");

        mockMvc.perform(get("/api/community/posts").param("company", "카카오"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.posts.length()").value(1))
                .andExpect(jsonPath("$.result.posts[0].company").value("카카오"));
    }

    @Test
    void position_필터_성공() throws Exception {
        User user = saveUser("position-filter@example.com", "직무필터작성자");
        savePost(user, CommunityPostCategory.PASS_REVIEW_INTERVIEW, CommunityJobCategory.B2C, "질문", "카카오", "백엔드", "Spring");
        savePost(user, CommunityPostCategory.PASS_REVIEW_INTERVIEW, CommunityJobCategory.B2C, "다른 질문", "카카오", "프론트엔드", "React");

        mockMvc.perform(get("/api/community/posts").param("jobRole", "프론트"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.posts.length()").value(1))
                .andExpect(jsonPath("$.result.posts[0].position").value("프론트엔드"));
    }

    @Test
    void techStack_필터_성공() throws Exception {
        User user = saveUser("tech-filter@example.com", "스택필터작성자");
        savePost(user, CommunityPostCategory.ROADMAP, CommunityJobCategory.B2C, "질문", "카카오", "백엔드", "Spring");
        savePost(user, CommunityPostCategory.PASS_REVIEW_INTERVIEW, CommunityJobCategory.B2C, "다른 질문", "카카오", "백엔드", "Docker");

        mockMvc.perform(get("/api/community/posts").param("techStack", "Docker"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.posts.length()").value(1))
                .andExpect(jsonPath("$.result.posts[0].techStacks[0]").value("Docker"));
    }

    @Test
    void 상세_조회시_조회수가_증가한다() throws Exception {
        User user = saveUser("detail@example.com", "상세작성자");
        CommunityPost post = savePost(user, CommunityPostCategory.ROADMAP, CommunityJobCategory.FINTECH, "상세 글", "토스", "백엔드", "Spring");

        mockMvc.perform(get("/api/community/posts/{postId}", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.viewCount").value(1))
                .andExpect(jsonPath("$.result.postCategory").value("ROADMAP"))
                .andExpect(jsonPath("$.result.jobCategory").value("FINTECH"))
                .andExpect(jsonPath("$.result.company").value("토스"))
                .andExpect(jsonPath("$.result.position").value("백엔드"))
                .andExpect(jsonPath("$.result.techStacks[0]").value("Spring"));
    }

    @Test
    void 좋아요_추가() throws Exception {
        User user = saveUser("like@example.com", "좋아요사용자");
        CommunityPost post = savePost(user, CommunityPostCategory.FREE, CommunityJobCategory.B2C, "좋아요 글", null, null, "Spring");

        mockMvc.perform(post("/api/community/posts/{postId}/like", post.getId())
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.liked").value(true))
                .andExpect(jsonPath("$.result.likeCount").value(1));
    }

    @Test
    void 좋아요_취소() throws Exception {
        User user = saveUser("unlike@example.com", "좋아요취소사용자");
        CommunityPost post = savePost(user, CommunityPostCategory.FREE, CommunityJobCategory.B2C, "좋아요 취소 글", null, null, "Spring");

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
        CommunityPost post = savePost(user, CommunityPostCategory.PASS_REVIEW_INTERVIEW, CommunityJobCategory.GENERALIST, "신고 글", "라인", "백엔드", "Spring");

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
        CommunityPost post = savePost(user, CommunityPostCategory.FREE, CommunityJobCategory.B2B, "댓글 글", "쿠팡", "백엔드", "Java");

        mockMvc.perform(post("/api/community/posts/{postId}/comments", post.getId())
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("댓글 내용", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content").value("댓글 내용"))
                .andExpect(jsonPath("$.result.authorName").value("댓글사용자"))
                .andExpect(jsonPath("$.result.parentId").doesNotExist())
                .andExpect(jsonPath("$.result.depth").value(0));
    }

    @Test
    void 대댓글_작성_성공() throws Exception {
        User user = saveUser("reply@example.com", "답글사용자");
        CommunityPost post = savePost(user, CommunityPostCategory.FREE, CommunityJobCategory.B2B, "답글 글", null, null, "Java");

        String rootResponse = mockMvc.perform(post("/api/community/posts/{postId}/comments", post.getId())
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("루트 댓글", null))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long parentCommentId = objectMapper.readTree(rootResponse).path("result").path("id").asLong();

        mockMvc.perform(post("/api/community/posts/{postId}/comments", post.getId())
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("대댓글", parentCommentId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content").value("대댓글"))
                .andExpect(jsonPath("$.result.parentId").value(parentCommentId))
                .andExpect(jsonPath("$.result.depth").value(1));
    }

    @Test
    void 대댓글에는_대댓글을_달수없다() throws Exception {
        User user = saveUser("reply-chain@example.com", "답글체인사용자");
        CommunityPost post = savePost(user, CommunityPostCategory.FREE, CommunityJobCategory.B2B, "답글 검증 글", null, null, "Java");

        String rootResponse = mockMvc.perform(post("/api/community/posts/{postId}/comments", post.getId())
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("루트 댓글", null))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long rootCommentId = objectMapper.readTree(rootResponse).path("result").path("id").asLong();

        String replyResponse = mockMvc.perform(post("/api/community/posts/{postId}/comments", post.getId())
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("대댓글", rootCommentId))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long replyCommentId = objectMapper.readTree(replyResponse).path("result").path("id").asLong();

        mockMvc.perform(post("/api/community/posts/{postId}/comments", post.getId())
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("대댓글의 대댓글", replyCommentId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 댓글_목록_조회시_대댓글_계층을_반환한다() throws Exception {
        User user = saveUser("comment-list@example.com", "댓글목록사용자");
        CommunityPost post = savePost(user, CommunityPostCategory.FREE, CommunityJobCategory.B2B, "댓글 목록 글", null, null, "Java");

        String rootResponse = mockMvc.perform(post("/api/community/posts/{postId}/comments", post.getId())
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("첫 댓글", null))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long rootCommentId = objectMapper.readTree(rootResponse).path("result").path("id").asLong();

        mockMvc.perform(post("/api/community/posts/{postId}/comments", post.getId())
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("첫 댓글의 답글", rootCommentId))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/community/posts/{postId}/comments", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(2))
                .andExpect(jsonPath("$.result[0].content").value("첫 댓글"))
                .andExpect(jsonPath("$.result[0].depth").value(0))
                .andExpect(jsonPath("$.result[1].content").value("첫 댓글의 답글"))
                .andExpect(jsonPath("$.result[1].depth").value(1))
                .andExpect(jsonPath("$.result[1].parentId").value(rootCommentId));
    }

    @Test
    void 본인_댓글_삭제_성공() throws Exception {
        User user = saveUser("comment-delete@example.com", "댓글삭제사용자");
        CommunityPost post = savePost(user, CommunityPostCategory.FREE, CommunityJobCategory.B2B, "댓글 삭제 글", null, null, "Java");

        String rootResponse = mockMvc.perform(post("/api/community/posts/{postId}/comments", post.getId())
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("삭제할 댓글", null))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long commentId = objectMapper.readTree(rootResponse).path("result").path("id").asLong();

        mockMvc.perform(delete("/api/community/comments/{commentId}", commentId)
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

        mockMvc.perform(get("/api/community/posts/{postId}/comments", post.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(0));
    }

    @Test
    void 타인_댓글_삭제는_실패한다() throws Exception {
        User writer = saveUser("comment-owner@example.com", "댓글주인");
        User other = saveUser("comment-other@example.com", "다른사용자");
        CommunityPost post = savePost(writer, CommunityPostCategory.FREE, CommunityJobCategory.B2B, "댓글 권한 글", null, null, "Java");

        String rootResponse = mockMvc.perform(post("/api/community/posts/{postId}/comments", post.getId())
                        .with(user(new UserDetailsImpl(writer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("삭제 불가 댓글", null))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long commentId = objectMapper.readTree(rootResponse).path("result").path("id").asLong();

        mockMvc.perform(delete("/api/community/comments/{commentId}", commentId)
                        .with(user(new UserDetailsImpl(other))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 댓글_중복_신고_방지() throws Exception {
        User user = saveUser("comment-report@example.com", "댓글신고사용자");
        CommunityPost post = savePost(user, CommunityPostCategory.FREE, CommunityJobCategory.B2B, "댓글 신고 글", null, null, "Java");

        String rootResponse = mockMvc.perform(post("/api/community/posts/{postId}/comments", post.getId())
                        .with(user(new UserDetailsImpl(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("신고 대상 댓글", null))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long commentId = objectMapper.readTree(rootResponse).path("result").path("id").asLong();

        mockMvc.perform(post("/api/community/comments/{commentId}/report", commentId)
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.reported").value(true));

        mockMvc.perform(post("/api/community/comments/{commentId}/report", commentId)
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 비로그인_사용자는_댓글_작성에_실패한다() throws Exception {
        User user = saveUser("anonymous@example.com", "익명대상");
        CommunityPost post = savePost(user, CommunityPostCategory.FREE, CommunityJobCategory.INFRA_DEVOPS, "인증 글", "당근", "DevOps", "Docker");

        mockMvc.perform(post("/api/community/posts/{postId}/comments", post.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CommentRequest("댓글 내용", null))))
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

    private CommunityPost savePost(
            User author,
            CommunityPostCategory postCategory,
            CommunityJobCategory jobCategory,
            String title,
            String company,
            String position,
            String techStack
    ) {
        CommunityPost post = CommunityPost.create(
                author,
                postCategory,
                jobCategory,
                title,
                title + " 본문",
                java.util.List.of()
        );

        if (postCategory == CommunityPostCategory.ROADMAP) {
            post.attachRoadmapDetail(CommunityRoadmapPostDetail.create(
                    post,
                    null,
                    title,
                    title + " 요약",
                    title + " 설명",
                    position,
                    company,
                    java.util.List.of(techStack)
            ));
        }

        if (postCategory == CommunityPostCategory.PASS_REVIEW_INTERVIEW) {
            post.attachInterviewDetail(CommunityInterviewPostDetail.create(
                    post,
                    CommunityInterviewSubtype.ACCEPTED,
                    company == null ? "미입력" : company,
                    position == null ? "미입력" : position,
                    "2개월",
                    java.util.List.of(techStack),
                    title + " 프로세스",
                    title + " 배경",
                    title + " 준비 과정",
                    title + " 상세",
                    title + " 조언"
            ));
        }

        return communityPostRepository.save(post);
    }

    private record CommentRequest(String content, Long parentCommentId) {
    }
}
