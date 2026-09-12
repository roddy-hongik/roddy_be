package com.roddy.domain.jobposting.controller;

import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.enums.RecruitType;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.domain.jobposting.dto.JobPostingSnapshot;
import com.roddy.domain.jobposting.entity.JobPosting;
import com.roddy.domain.jobposting.repository.JobBookmarkRepository;
import com.roddy.domain.jobposting.repository.JobPostingRepository;
import com.roddy.global.config.s3.S3Uploader;
import com.roddy.global.security.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class JobPostingControllerTest {

    private static final LocalDateTime CRAWLED_AT = LocalDateTime.of(2026, 3, 16, 4, 0);

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private JobBookmarkRepository jobBookmarkRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private S3Uploader s3Uploader;

    @MockitoBean
    private SocialAuthService socialAuthService;

    private MockMvc mockMvc;

    /** 다른 테스트가 남긴 유저까지 지우면 그쪽 외래키가 깨지므로, 이 테스트가 만든 유저만 정리한다. */
    private final List<User> createdUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        jobBookmarkRepository.deleteAll();
        jobPostingRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        jobBookmarkRepository.deleteAll();
        jobPostingRepository.deleteAll();
        userRepository.deleteAll(createdUsers);
        createdUsers.clear();
    }

    @Test
    @DisplayName("로그인하지 않아도 공고 목록을 최신순으로 볼 수 있다")
    void getJobPostingsWithoutLogin() throws Exception {
        savePosting("P-1", "백엔드 개발자", builder -> builder.postedAt(LocalDateTime.of(2026, 3, 10, 0, 0)));
        savePosting("P-2", "프론트엔드 개발자", builder -> builder.postedAt(LocalDateTime.of(2026, 3, 14, 0, 0)));
        savePosting("P-3", "게시일 없는 공고", builder -> builder.postedAt(null));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.totalElements").value(3))
                .andExpect(jsonPath("$.result.jobs[0].title").value("프론트엔드 개발자"))
                .andExpect(jsonPath("$.result.jobs[1].title").value("백엔드 개발자"))
                .andExpect(jsonPath("$.result.jobs[2].title").value("게시일 없는 공고"))
                .andExpect(jsonPath("$.result.jobs[0].isScrapped").value(false));
    }

    @Test
    @DisplayName("회사명과 공고 제목에서 검색한다")
    void searchesByKeyword() throws Exception {
        savePosting("P-1", "백엔드 개발자", builder -> builder.company("카카오"));
        savePosting("P-2", "프론트엔드 개발자", builder -> builder.company("네이버").companyCode("naver"));

        mockMvc.perform(get("/api/jobs").param("keyword", "백엔드"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.jobs[0].title").value("백엔드 개발자"));

        mockMvc.perform(get("/api/jobs").param("keyword", "네이버"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.jobs[0].company").value("네이버"));
    }

    @Test
    @DisplayName("회사 코드와 채용 구분으로 거른다")
    void filtersByCompanyAndRecruitType() throws Exception {
        savePosting("P-1", "카카오 백엔드", builder -> builder.recruitType(RecruitType.JUNIOR));
        savePosting("P-2", "카카오 시니어", builder -> builder.recruitType(RecruitType.SENIOR));
        savePosting("N-1", "네이버 백엔드", builder -> builder.companyCode("naver").company("네이버"));

        mockMvc.perform(get("/api/jobs").param("company", "naver"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.jobs[0].companyCode").value("naver"));

        mockMvc.perform(get("/api/jobs").param("recruitType", "JUNIOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.jobs[0].experience").value("신입"));
    }

    @Test
    @DisplayName("기본은 모집중인 공고만 보여주고, 마감 공고는 따로 조회한다")
    void showsOpenPostingsByDefault() throws Exception {
        savePosting("P-1", "모집중 공고", builder -> {
        });
        savePosting("P-2", "마감된 공고", builder -> builder.closed(true));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.jobs[0].title").value("모집중 공고"));

        mockMvc.perform(get("/api/jobs").param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.jobs[0].title").value("마감된 공고"));
    }

    @Test
    @DisplayName("페이지 단위로 끊어서 준다")
    void paginatesResult() throws Exception {
        for (int i = 1; i <= 5; i++) {
            int day = i;
            savePosting("P-" + i, "공고 " + i, builder -> builder.postedAt(LocalDateTime.of(2026, 3, day, 0, 0)));
        }

        mockMvc.perform(get("/api/jobs").param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(5))
                .andExpect(jsonPath("$.result.totalPages").value(3))
                .andExpect(jsonPath("$.result.jobs.length()").value(2))
                .andExpect(jsonPath("$.result.jobs[0].title").value("공고 5"));

        mockMvc.perform(get("/api/jobs").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.page").value(1))
                .andExpect(jsonPath("$.result.jobs.length()").value(2))
                .andExpect(jsonPath("$.result.jobs[0].title").value("공고 3"))
                .andExpect(jsonPath("$.result.jobs[1].title").value("공고 2"));
    }

    @Test
    @DisplayName("공고 상세를 조회한다")
    void getJobPostingDetail() throws Exception {
        JobPosting posting = savePosting("P-1", "백엔드 개발자",
                builder -> builder.content("서버를 개발합니다.").location("판교").employmentType("정규직"));

        mockMvc.perform(get("/api/jobs/{jobPostingId}", posting.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.title").value("백엔드 개발자"))
                .andExpect(jsonPath("$.result.content").value("서버를 개발합니다."))
                .andExpect(jsonPath("$.result.location").value("판교"))
                .andExpect(jsonPath("$.result.workType").value("정규직"))
                .andExpect(jsonPath("$.result.applyUrl").value("https://careers.kakao.com/jobs/P-1"))
                .andExpect(jsonPath("$.result.isScrapped").value(false));
    }

    @Test
    @DisplayName("뽑아 둔 요구 기술스택을 목록과 상세에 함께 준다")
    void returnsTechStacks() throws Exception {
        JobPosting posting = savePostingWithTechStacks("P-1", "백엔드 개발자", Set.of("Java", "Spring Boot"));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.jobs[0].techStacks", containsInAnyOrder("Java", "Spring Boot")));

        mockMvc.perform(get("/api/jobs/{jobPostingId}", posting.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.techStacks", containsInAnyOrder("Java", "Spring Boot")));
    }

    @Test
    @DisplayName("스크랩한 사용자에게는 상세에서도 스크랩 상태를 알려준다")
    void marksScrappedPostingInDetail() throws Exception {
        User user = saveUser("detail-scrap@example.com", "상세스크랩유저");
        JobPosting posting = savePosting("P-1", "백엔드 개발자", builder -> {
        });

        mockMvc.perform(post("/api/jobs/{jobPostingId}/scrap", posting.getId())
                .with(user(new UserDetailsImpl(user))));

        mockMvc.perform(get("/api/jobs/{jobPostingId}", posting.getId())
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isScrapped").value(true));
    }

    @Test
    @DisplayName("없는 공고를 조회하면 알려준다")
    void returnsNotFoundForMissingPosting() throws Exception {
        mockMvc.perform(get("/api/jobs/{jobPostingId}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("JOB_4041"));
    }

    @Test
    @DisplayName("스크랩을 담고 다시 누르면 해제한다")
    void togglesScrap() throws Exception {
        User user = saveUser("scrap@example.com", "스크랩유저");
        JobPosting posting = savePosting("P-1", "백엔드 개발자", builder -> {
        });

        mockMvc.perform(post("/api/jobs/{jobPostingId}/scrap", posting.getId())
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isScrapped").value(true));

        mockMvc.perform(post("/api/jobs/{jobPostingId}/scrap", posting.getId())
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isScrapped").value(false));
    }

    @Test
    @DisplayName("로그인한 사용자에게는 목록에 스크랩 여부를 함께 준다")
    void marksScrappedPostingsInList() throws Exception {
        User user = saveUser("marker@example.com", "표시유저");
        JobPosting scrapped = savePosting("P-1", "스크랩한 공고",
                builder -> builder.postedAt(LocalDateTime.of(2026, 3, 14, 0, 0)));
        savePosting("P-2", "스크랩하지 않은 공고",
                builder -> builder.postedAt(LocalDateTime.of(2026, 3, 10, 0, 0)));

        mockMvc.perform(post("/api/jobs/{jobPostingId}/scrap", scrapped.getId())
                .with(user(new UserDetailsImpl(user))));

        mockMvc.perform(get("/api/jobs").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.jobs[0].title").value("스크랩한 공고"))
                .andExpect(jsonPath("$.result.jobs[0].isScrapped").value(true))
                .andExpect(jsonPath("$.result.jobs[1].title").value("스크랩하지 않은 공고"))
                .andExpect(jsonPath("$.result.jobs[1].isScrapped").value(false));
    }

    @Test
    @DisplayName("내가 스크랩한 공고만 모아 준다")
    void getMyScraps() throws Exception {
        User user = saveUser("mine@example.com", "내유저");
        User other = saveUser("other@example.com", "다른유저");
        JobPosting mine = savePosting("P-1", "내가 담은 공고", builder -> {
        });
        JobPosting theirs = savePosting("P-2", "남이 담은 공고", builder -> {
        });

        mockMvc.perform(post("/api/jobs/{jobPostingId}/scrap", mine.getId())
                .with(user(new UserDetailsImpl(user))));
        mockMvc.perform(post("/api/jobs/{jobPostingId}/scrap", theirs.getId())
                .with(user(new UserDetailsImpl(other))));

        mockMvc.perform(get("/api/jobs/scraps/me").with(user(new UserDetailsImpl(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].title").value("내가 담은 공고"))
                .andExpect(jsonPath("$.result[0].isScrapped").value(true));
    }

    @Test
    @DisplayName("없는 공고는 스크랩할 수 없다")
    void rejectsScrapForMissingPosting() throws Exception {
        User user = saveUser("missing-scrap@example.com", "없는공고유저");

        mockMvc.perform(post("/api/jobs/{jobPostingId}/scrap", 999_999L)
                        .with(user(new UserDetailsImpl(user))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("JOB_4041"));
    }

    @Test
    @DisplayName("로그인하지 않으면 스크랩할 수 없다")
    void rejectsScrapWithoutLogin() throws Exception {
        JobPosting posting = savePosting("P-1", "백엔드 개발자", builder -> {
        });

        mockMvc.perform(post("/api/jobs/{jobPostingId}/scrap", posting.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("로그인하지 않으면 스크랩 목록도 볼 수 없다")
    void rejectsMyScrapsWithoutLogin() throws Exception {
        // /api/jobs/* 는 한 칸짜리 경로만 열려 있어 /api/jobs/scraps/me 는 인증이 유지된다.
        mockMvc.perform(get("/api/jobs/scraps/me"))
                .andExpect(status().isUnauthorized());
    }

    private JobPosting savePosting(String externalId, String title,
                                   Consumer<JobPostingSnapshot.JobPostingSnapshotBuilder> customizer) {
        JobPostingSnapshot.JobPostingSnapshotBuilder builder = JobPostingSnapshot.builder()
                .companyCode("kakao")
                .company("카카오")
                .externalId(externalId)
                .title(title)
                .applyUrl("https://careers.kakao.com/jobs/" + externalId);
        customizer.accept(builder);

        return jobPostingRepository.save(JobPosting.create(builder.build(), CRAWLED_AT));
    }

    private JobPosting savePostingWithTechStacks(String externalId, String title, Set<String> techStacks) {
        JobPosting posting = savePosting(externalId, title, builder -> {
        });
        posting.updateTechStacks(techStacks);
        return jobPostingRepository.save(posting);
    }

    private User saveUser(String email, String nickname) {
        User saved = userRepository.save(
                User.builder()
                        .email(email)
                        .password("encoded-password")
                        .socialType(SocialType.LOCAL)
                        .nickname(nickname)
                        .username(nickname)
                        .role(Role.USER)
                        .build()
        );
        createdUsers.add(saved);
        return saved;
    }
}
