package com.roddy.domain.study.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.community.repository.CommunityCommentRepository;
import com.roddy.domain.community.repository.CommunityPostImageRepository;
import com.roddy.domain.community.repository.CommunityPostLikeRepository;
import com.roddy.domain.community.repository.CommunityPostReportRepository;
import com.roddy.domain.community.repository.CommunityPostRepository;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.domain.study.entity.StudyApplication;
import com.roddy.domain.study.entity.StudyPost;
import com.roddy.domain.study.enums.StudyApplicationStatus;
import com.roddy.domain.study.enums.StudyMode;
import com.roddy.domain.study.enums.StudyRecruitStatus;
import com.roddy.domain.study.repository.StudyApplicationRepository;
import com.roddy.domain.study.repository.StudyPostRepository;
import com.roddy.global.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.nullValue;
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
class StudyControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StudyApplicationRepository studyApplicationRepository;

    @Autowired
    private StudyPostRepository studyPostRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommunityPostLikeRepository communityPostLikeRepository;

    @Autowired
    private CommunityPostReportRepository communityPostReportRepository;

    @Autowired
    private CommunityCommentRepository communityCommentRepository;

    @Autowired
    private CommunityPostImageRepository communityPostImageRepository;

    @Autowired
    private CommunityPostRepository communityPostRepository;

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
        studyApplicationRepository.deleteAll();
        studyPostRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 스터디_모집글_작성_성공() throws Exception {
        User author = saveUser("author1@example.com", "작성자1");

        mockMvc.perform(post("/api/studies")
                        .with(user(new UserDetailsImpl(author)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateStudyRequest(
                                "백엔드 면접 대비 스터디 모집",
                                "Spring Boot, JPA, CS 면접 질문을 정리합니다.",
                                "OFFLINE",
                                "강남역 인근 스터디룸",
                                LocalDateTime.now().plusDays(3),
                                4
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").isNumber());
    }

    @Test
    void OFFLINE인데_location이_없으면_실패() throws Exception {
        User author = saveUser("author2@example.com", "작성자2");

        mockMvc.perform(post("/api/studies")
                        .with(user(new UserDetailsImpl(author)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateStudyRequest(
                                "오프라인 스터디",
                                "내용",
                                "OFFLINE",
                                null,
                                LocalDateTime.now().plusDays(1),
                                4
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void scheduledAt이_과거이면_실패() throws Exception {
        User author = saveUser("author3@example.com", "작성자3");

        mockMvc.perform(post("/api/studies")
                        .with(user(new UserDetailsImpl(author)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateStudyRequest(
                                "지난 일정 스터디",
                                "내용",
                                "ONLINE",
                                null,
                                LocalDateTime.now().minusDays(1),
                                4
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 스터디_목록_조회_성공() throws Exception {
        User author = saveUser("list-author@example.com", "목록작성자");
        saveStudy(author, "백엔드 스터디", "Spring Boot", StudyMode.OFFLINE, "강남", LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.RECRUITING);
        saveStudy(author, "알고리즘 스터디", "PS", StudyMode.ONLINE, "Discord", LocalDateTime.now().plusDays(3), 6, 2, StudyRecruitStatus.CLOSED);

        mockMvc.perform(get("/api/studies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.studies.length()").value(2))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20));
    }

    @Test
    void status_필터_조회_성공() throws Exception {
        User author = saveUser("status-author@example.com", "상태작성자");
        saveStudy(author, "모집중 스터디", "내용", StudyMode.OFFLINE, "강남", LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.RECRUITING);
        saveStudy(author, "마감 스터디", "내용", StudyMode.ONLINE, null, LocalDateTime.now().plusDays(2), 4, 4, StudyRecruitStatus.CLOSED);

        mockMvc.perform(get("/api/studies").param("status", "RECRUITING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.studies.length()").value(1))
                .andExpect(jsonPath("$.result.studies[0].status").value("RECRUITING"));
    }

    @Test
    void mode_필터_조회_성공() throws Exception {
        User author = saveUser("mode-author@example.com", "모드작성자");
        saveStudy(author, "대면 스터디", "내용", StudyMode.OFFLINE, "강남", LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.RECRUITING);
        saveStudy(author, "비대면 스터디", "내용", StudyMode.ONLINE, "Discord", LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.RECRUITING);

        mockMvc.perform(get("/api/studies").param("mode", "ONLINE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.studies.length()").value(1))
                .andExpect(jsonPath("$.result.studies[0].mode").value("ONLINE"));
    }

    @Test
    void keyword_검색_성공() throws Exception {
        User author = saveUser("keyword-author@example.com", "검색작성자");
        saveStudy(author, "백엔드 면접 대비", "Spring Boot 질문", StudyMode.OFFLINE, "강남역 스터디룸", LocalDateTime.now().plusDays(2), 4, 0, StudyRecruitStatus.RECRUITING);
        saveStudy(author, "프론트 스터디", "React", StudyMode.ONLINE, "Google Meet", LocalDateTime.now().plusDays(2), 4, 0, StudyRecruitStatus.RECRUITING);

        mockMvc.perform(get("/api/studies").param("keyword", "강남역"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.studies.length()").value(1))
                .andExpect(jsonPath("$.result.studies[0].title").value("백엔드 면접 대비"));
    }

    @Test
    void 스터디_상세_조회_성공() throws Exception {
        User author = saveUser("detail-author@example.com", "상세작성자");
        StudyPost studyPost = saveStudy(author, "상세 스터디", "상세 내용", StudyMode.OFFLINE, "강남", LocalDateTime.now().plusDays(2), 4, 0, StudyRecruitStatus.RECRUITING);

        mockMvc.perform(get("/api/studies/{studyId}", studyPost.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(studyPost.getId()))
                .andExpect(jsonPath("$.result.authorName").value("상세작성자"))
                .andExpect(jsonPath("$.result.myApplicationStatus").value(nullValue()))
                .andExpect(jsonPath("$.result.applicants.length()").value(0));
    }

    @Test
    void 로그인_사용자가_상세_조회시_myApplicationStatus_반환() throws Exception {
        User author = saveUser("detail-author2@example.com", "상세작성자2");
        User applicant = saveUser("detail-applicant@example.com", "지원자");
        StudyPost studyPost = saveStudy(author, "지원 상태 확인", "내용", StudyMode.ONLINE, "Discord", LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.RECRUITING);
        saveApplication(studyPost, applicant, StudyApplicationStatus.APPLIED);

        mockMvc.perform(get("/api/studies/{studyId}", studyPost.getId())
                        .with(user(new UserDetailsImpl(applicant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.myApplicationStatus").value("APPLIED"))
                .andExpect(jsonPath("$.result.myApplicationStatusDisplayName").value("지원 완료"));
    }

    @Test
    void 작성자가_상세_조회시_지원자_목록_반환() throws Exception {
        User author = saveUser("manager-author@example.com", "모집자");
        User applicant = saveUser("manager-applicant@example.com", "지원자A");
        StudyPost studyPost = saveStudy(author, "모집자 상세", "내용", StudyMode.ONLINE, "Discord", LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.RECRUITING);
        saveApplication(studyPost, applicant, StudyApplicationStatus.APPLIED);

        mockMvc.perform(get("/api/studies/{studyId}", studyPost.getId())
                        .with(user(new UserDetailsImpl(author))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isAuthor").value(true))
                .andExpect(jsonPath("$.result.applicants.length()").value(1))
                .andExpect(jsonPath("$.result.applicants[0].applicantName").value("지원자A"))
                .andExpect(jsonPath("$.result.applicants[0].status").value("APPLIED"));
    }

    @Test
    void 스터디_지원_성공() throws Exception {
        User author = saveUser("apply-author@example.com", "작성자");
        User applicant = saveUser("apply-user@example.com", "지원자");
        StudyPost studyPost = saveStudy(author, "지원 스터디", "내용", StudyMode.ONLINE, "Discord", LocalDateTime.now().plusDays(2), 4, 0, StudyRecruitStatus.RECRUITING);

        mockMvc.perform(post("/api/studies/{studyId}/applications", studyPost.getId())
                        .with(user(new UserDetailsImpl(applicant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("APPLIED"))
                .andExpect(jsonPath("$.result.applicantCount").value(1));
    }

    @Test
    void 모집_완료된_스터디_지원_실패() throws Exception {
        User author = saveUser("closed-author@example.com", "작성자");
        User applicant = saveUser("closed-user@example.com", "지원자");
        StudyPost studyPost = saveStudy(author, "마감 스터디", "내용", StudyMode.ONLINE, null, LocalDateTime.now().plusDays(2), 4, 0, StudyRecruitStatus.CLOSED);

        mockMvc.perform(post("/api/studies/{studyId}/applications", studyPost.getId())
                        .with(user(new UserDetailsImpl(applicant))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 모집_인원_초과시_지원_실패() throws Exception {
        User author = saveUser("full-author@example.com", "작성자");
        User applicant = saveUser("full-user@example.com", "지원자");
        StudyPost studyPost = saveStudy(author, "정원 마감 직전", "내용", StudyMode.ONLINE, null, LocalDateTime.now().plusDays(2), 1, 1, StudyRecruitStatus.RECRUITING);

        mockMvc.perform(post("/api/studies/{studyId}/applications", studyPost.getId())
                        .with(user(new UserDetailsImpl(applicant))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 작성자가_자기_스터디에_지원하면_실패() throws Exception {
        User author = saveUser("same-author@example.com", "작성자");
        StudyPost studyPost = saveStudy(author, "내 스터디", "내용", StudyMode.ONLINE, null, LocalDateTime.now().plusDays(2), 4, 0, StudyRecruitStatus.RECRUITING);

        mockMvc.perform(post("/api/studies/{studyId}/applications", studyPost.getId())
                        .with(user(new UserDetailsImpl(author))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 중복_지원_실패() throws Exception {
        User author = saveUser("dup-author@example.com", "작성자");
        User applicant = saveUser("dup-user@example.com", "지원자");
        StudyPost studyPost = saveStudy(author, "중복 지원 테스트", "내용", StudyMode.ONLINE, null, LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.RECRUITING);
        saveApplication(studyPost, applicant, StudyApplicationStatus.APPLIED);

        mockMvc.perform(post("/api/studies/{studyId}/applications", studyPost.getId())
                        .with(user(new UserDetailsImpl(applicant))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 지원_취소_성공() throws Exception {
        User author = saveUser("cancel-author@example.com", "작성자");
        User applicant = saveUser("cancel-user@example.com", "지원자");
        StudyPost studyPost = saveStudy(author, "취소 테스트", "내용", StudyMode.ONLINE, null, LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.RECRUITING);
        saveApplication(studyPost, applicant, StudyApplicationStatus.APPLIED);

        mockMvc.perform(delete("/api/studies/{studyId}/applications/me", studyPost.getId())
                        .with(user(new UserDetailsImpl(applicant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CANCELED"))
                .andExpect(jsonPath("$.result.applicantCount").value(0));
    }

    @Test
    void 모집자가_지원자를_수락할_수_있다() throws Exception {
        User author = saveUser("approve-author@example.com", "작성자");
        User applicant = saveUser("approve-user@example.com", "지원자");
        StudyPost studyPost = saveStudy(author, "승인 테스트", "내용", StudyMode.ONLINE, "Discord", LocalDateTime.now().plusDays(2), 1, 1, StudyRecruitStatus.RECRUITING);
        StudyApplication application = saveApplication(studyPost, applicant, StudyApplicationStatus.APPLIED);

        mockMvc.perform(patch("/api/studies/{studyId}/applications/{applicationId}", studyPost.getId(), application.getId())
                        .with(user(new UserDetailsImpl(author)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest("ACCEPTED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.result.statusDisplayName").value("수락"))
                .andExpect(jsonPath("$.result.applicantCount").value(1));

        mockMvc.perform(get("/api/studies/{studyId}", studyPost.getId())
                        .with(user(new UserDetailsImpl(author))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CLOSED"));
    }

    @Test
    void 모집자가_지원자를_거절하면_활성_지원자수가_감소한다() throws Exception {
        User author = saveUser("reject-author@example.com", "작성자");
        User applicant = saveUser("reject-user@example.com", "지원자");
        StudyPost studyPost = saveStudy(author, "거절 테스트", "내용", StudyMode.ONLINE, "Discord", LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.RECRUITING);
        StudyApplication application = saveApplication(studyPost, applicant, StudyApplicationStatus.APPLIED);

        mockMvc.perform(patch("/api/studies/{studyId}/applications/{applicationId}", studyPost.getId(), application.getId())
                        .with(user(new UserDetailsImpl(author)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest("REJECTED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("REJECTED"))
                .andExpect(jsonPath("$.result.statusDisplayName").value("거절"))
                .andExpect(jsonPath("$.result.applicantCount").value(0));
    }

    @Test
    void 작성자가_아닌_사용자는_지원_상태를_변경할_수_없다() throws Exception {
        User author = saveUser("forbidden-author@example.com", "작성자");
        User applicant = saveUser("forbidden-applicant@example.com", "지원자");
        User otherUser = saveUser("forbidden-other@example.com", "다른유저");
        StudyPost studyPost = saveStudy(author, "권한 테스트", "내용", StudyMode.ONLINE, "Discord", LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.RECRUITING);
        StudyApplication application = saveApplication(studyPost, applicant, StudyApplicationStatus.APPLIED);

        mockMvc.perform(patch("/api/studies/{studyId}/applications/{applicationId}", studyPost.getId(), application.getId())
                        .with(user(new UserDetailsImpl(otherUser)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest("ACCEPTED"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 이미_처리된_지원은_다시_변경할_수_없다() throws Exception {
        User author = saveUser("processed-author@example.com", "작성자");
        User applicant = saveUser("processed-applicant@example.com", "지원자");
        StudyPost studyPost = saveStudy(author, "처리 완료 테스트", "내용", StudyMode.ONLINE, "Discord", LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.RECRUITING);
        StudyApplication application = saveApplication(studyPost, applicant, StudyApplicationStatus.ACCEPTED);

        mockMvc.perform(patch("/api/studies/{studyId}/applications/{applicationId}", studyPost.getId(), application.getId())
                        .with(user(new UserDetailsImpl(author)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateApplicationStatusRequest("REJECTED"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 거절된_지원_취소시_다른_활성_지원자_카운트는_유지된다() throws Exception {
        User author = saveUser("cancel-rejected-author@example.com", "작성자");
        User rejectedApplicant = saveUser("cancel-rejected-user@example.com", "거절지원자");
        User activeApplicant = saveUser("cancel-active-user@example.com", "활성지원자");
        StudyPost studyPost = saveStudy(author, "거절 취소 테스트", "내용", StudyMode.ONLINE, null, LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.RECRUITING);
        saveApplication(studyPost, rejectedApplicant, StudyApplicationStatus.REJECTED);
        saveApplication(studyPost, activeApplicant, StudyApplicationStatus.APPLIED);

        mockMvc.perform(delete("/api/studies/{studyId}/applications/me", studyPost.getId())
                        .with(user(new UserDetailsImpl(rejectedApplicant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CANCELED"))
                .andExpect(jsonPath("$.result.applicantCount").value(1));
    }

    @Test
    void 모집글_작성자가_모집_완료_처리_성공() throws Exception {
        User author = saveUser("close-author@example.com", "작성자");
        StudyPost studyPost = saveStudy(author, "마감 처리", "내용", StudyMode.ONLINE, null, LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.RECRUITING);

        mockMvc.perform(patch("/api/studies/{studyId}/close", studyPost.getId())
                        .with(user(new UserDetailsImpl(author))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CLOSED"));
    }

    @Test
    void 작성자가_아닌_사용자가_모집_완료_처리하면_실패() throws Exception {
        User author = saveUser("close-author2@example.com", "작성자");
        User otherUser = saveUser("close-other@example.com", "다른유저");
        StudyPost studyPost = saveStudy(author, "권한 테스트", "내용", StudyMode.ONLINE, null, LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.RECRUITING);

        mockMvc.perform(patch("/api/studies/{studyId}/close", studyPost.getId())
                        .with(user(new UserDetailsImpl(otherUser))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 모집글_작성자가_스터디를_재오픈할_수_있다() throws Exception {
        User author = saveUser("reopen-author@example.com", "작성자");
        StudyPost studyPost = saveStudy(author, "재오픈 테스트", "내용", StudyMode.ONLINE, null, LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.CLOSED);

        mockMvc.perform(patch("/api/studies/{studyId}/reopen", studyPost.getId())
                        .with(user(new UserDetailsImpl(author))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("RECRUITING"))
                .andExpect(jsonPath("$.result.statusDisplayName").value("모집중"));
    }

    @Test
    void 확정_인원이_가득찬_스터디는_재오픈할_수_없다() throws Exception {
        User author = saveUser("reopen-full-author@example.com", "작성자");
        User applicant = saveUser("reopen-full-applicant@example.com", "지원자");
        StudyPost studyPost = saveStudy(author, "재오픈 불가 테스트", "내용", StudyMode.ONLINE, null, LocalDateTime.now().plusDays(2), 1, 1, StudyRecruitStatus.CLOSED);
        saveApplication(studyPost, applicant, StudyApplicationStatus.ACCEPTED);

        mockMvc.perform(patch("/api/studies/{studyId}/reopen", studyPost.getId())
                        .with(user(new UserDetailsImpl(author))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    void 내가_지원한_스터디_목록_조회_성공() throws Exception {
        User author = saveUser("my-app-author@example.com", "작성자");
        User applicant = saveUser("my-app-user@example.com", "지원자");
        StudyPost studyPost = saveStudy(author, "내 지원 목록", "내용", StudyMode.OFFLINE, "강남", LocalDateTime.now().plusDays(2), 4, 1, StudyRecruitStatus.RECRUITING);
        saveApplication(studyPost, applicant, StudyApplicationStatus.APPLIED);

        mockMvc.perform(get("/api/studies/applications/me")
                        .with(user(new UserDetailsImpl(applicant))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.applications.length()").value(1))
                .andExpect(jsonPath("$.result.applications[0].studyId").value(studyPost.getId()))
                .andExpect(jsonPath("$.result.applications[0].applicationStatus").value("APPLIED"));
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

    private StudyPost saveStudy(
            User author,
            String title,
            String content,
            StudyMode mode,
            String location,
            LocalDateTime scheduledAt,
            int capacity,
            int applicantCount,
            StudyRecruitStatus status
    ) {
        StudyPost studyPost = StudyPost.create(author, title, content, mode, location, scheduledAt, capacity);
        if (status == StudyRecruitStatus.CLOSED) {
            studyPost.close();
        }
        for (int i = 0; i < applicantCount; i++) {
            studyPost.increaseApplicantCount();
        }
        return studyPostRepository.save(studyPost);
    }

    private StudyApplication saveApplication(StudyPost studyPost, User applicant, StudyApplicationStatus status) {
        StudyApplication application = StudyApplication.create(studyPost, applicant);
        if (status == StudyApplicationStatus.ACCEPTED) {
            application.accept();
        } else if (status == StudyApplicationStatus.REJECTED) {
            application.reject();
        } else if (status == StudyApplicationStatus.CANCELED) {
            application.cancel();
        }
        return studyApplicationRepository.save(application);
    }

    private record CreateStudyRequest(
            String title,
            String content,
            String mode,
            String location,
            LocalDateTime scheduledAt,
            Integer capacity
    ) {
    }

    private record UpdateApplicationStatusRequest(
            String status
    ) {
    }

    @TestConfiguration
    static class TestWebClientConfig {

        @Bean
        WebClient.Builder webClientBuilder() {
            return WebClient.builder();
        }
    }
}
