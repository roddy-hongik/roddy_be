package com.roddy.domain.analysis.service;

import com.roddy.domain.analysis.entity.AnalysisReport;
import com.roddy.domain.analysis.entity.AnalysisReportCategory;
import com.roddy.domain.analysis.enums.AnalysisStatus;
import com.roddy.domain.analysis.repository.AnalysisReportCategoryRepository;
import com.roddy.domain.analysis.repository.AnalysisReportRepository;
import com.roddy.domain.analysis.repository.StackDetailRepository;
import com.roddy.domain.analysis.repository.UserStackRepository;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.enums.ExperienceLevel;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.domain.enums.StackLevel;
import com.roddy.global.client.analysis.AnalysisAiResponse;
import com.roddy.global.client.analysis.AnalysisAiResponse.AnalyzedStack;
import com.roddy.global.client.analysis.AnalysisAiResponse.CategoryScore;
import com.roddy.global.config.s3.S3ObjectUrlService;
import com.roddy.global.config.s3.S3Uploader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
class AnalysisReportStoreTest {

    @Autowired
    private AnalysisReportStore analysisReportStore;

    @Autowired
    private AnalysisReportRepository analysisReportRepository;

    @Autowired
    private AnalysisReportCategoryRepository analysisReportCategoryRepository;

    @Autowired
    private UserStackRepository userStackRepository;

    @Autowired
    private StackDetailRepository stackDetailRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private S3Uploader s3Uploader;

    @MockitoBean
    private S3ObjectUrlService s3ObjectUrlService;

    @MockitoBean
    private SocialAuthService socialAuthService;

    private final List<User> createdUsers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clear();
    }

    @AfterEach
    void tearDown() {
        clear();
        userRepository.deleteAll(createdUsers);
        createdUsers.clear();
    }

    @Test
    @DisplayName("분석을 시작하면 진행 중인 리포트를 새로 만든다")
    void createsPendingReport() {
        User user = saveUser("pending@example.com");

        Long reportId = analysisReportStore.createPending(user.getId());

        AnalysisReport report = analysisReportRepository.findById(reportId).orElseThrow();
        assertThat(report.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(report.getTitle()).isNull();
        assertThat(analysisReportStore.isAnalyzing(user.getId())).isTrue();
    }

    @Test
    @DisplayName("리포트에 분석을 요청한 당시의 직무를 남긴다")
    void keepsDesiredJobAtRequest() {
        User user = saveUser("job@example.com");
        changeDesiredJob(user, DesiredJob.BACKEND);

        Long reportId = analysisReportStore.createPending(user.getId());
        // 평가 축이 직무마다 다르다. 요청한 뒤 직무를 바꿔도 이 리포트는 백엔드 축으로 채점해야 한다.
        changeDesiredJob(user, DesiredJob.FRONTEND);

        assertThat(analysisReportStore.findDesiredJob(reportId)).isEqualTo(DesiredJob.BACKEND);
    }

    @Test
    @DisplayName("분석 결과를 리포트와 기술스택으로 저장한다")
    void savesReportAndStacks() {
        User user = saveUser("complete@example.com");
        Long reportId = analysisReportStore.createPending(user.getId());

        analysisReportStore.complete(reportId, response(
                stack("Java", 80, "INTERMEDIATE", "저장소 대부분이 자바다"),
                stack("Spring Boot", 70, "INTERMEDIATE", "API 서버를 여러 번 만들었다")));

        AnalysisReport report = analysisReportRepository.findById(reportId).orElseThrow();
        assertThat(report.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(report.getTitle()).isEqualTo("백엔드 주니어");
        assertThat(report.getTotalScore()).isEqualTo(72);
        assertThat(report.getAnalyzedAt()).isNotNull();
        assertThat(analysisReportStore.isAnalyzing(user.getId())).isFalse();

        assertThat(analysisReportStore.findStacks(reportId))
                .extracting(stack -> stack.getStackDetail().getStackName())
                .containsExactlyInAnyOrder("Java", "Spring Boot");
    }

    @Test
    @DisplayName("숙련도 단계를 함께 저장하고, 모르는 값이면 비워 둔다")
    void savesStackLevel() {
        User user = saveUser("level@example.com");
        Long reportId = analysisReportStore.createPending(user.getId());

        analysisReportStore.complete(reportId, response(
                stack("Java", 80, "ADVANCED", "설명"),
                stack("Kotlin", 60, "알 수 없는 값", "설명")));

        assertThat(analysisReportStore.findStacks(reportId))
                .filteredOn(stack -> stack.getStackDetail().getStackName().equals("Java"))
                .singleElement()
                .satisfies(stack -> assertThat(stack.getStackLevel()).isEqualTo(StackLevel.ADVANCED));
        assertThat(analysisReportStore.findStacks(reportId))
                .filteredOn(stack -> stack.getStackDetail().getStackName().equals("Kotlin"))
                .singleElement()
                .satisfies(stack -> assertThat(stack.getStackLevel()).isNull());
    }

    @Test
    @DisplayName("한글로 온 기술 이름을 공고와 이어지는 표준 이름으로 바꿔 저장한다")
    void canonicalizesStackName() {
        User user = saveUser("korean@example.com");
        Long reportId = analysisReportStore.createPending(user.getId());

        analysisReportStore.complete(reportId, response(stack("자바", 80, "INTERMEDIATE", "설명")));

        assertThat(analysisReportStore.findStacks(reportId))
                .singleElement()
                .satisfies(stack -> assertThat(stack.getStackDetail().getStackName()).isEqualTo("Java"));
    }

    @Test
    @DisplayName("같은 기술을 두 번 내놓아도 한 번만 저장한다")
    void dedupesStacks() {
        User user = saveUser("duplicate@example.com");
        Long reportId = analysisReportStore.createPending(user.getId());

        // 표준 이름으로 맞추면 둘 다 Java 가 된다.
        analysisReportStore.complete(reportId, response(
                stack("Java", 80, "INTERMEDIATE", "먼저 온 설명"),
                stack("자바", 40, "BEGINNER", "나중에 온 설명")));

        assertThat(analysisReportStore.findStacks(reportId))
                .singleElement()
                .satisfies(stack -> assertThat(stack.getScore()).isEqualTo(80));
    }

    @Test
    @DisplayName("다시 분석하면 리포트를 새로 쌓고, 지난 리포트와 그 기술스택은 남긴다")
    void keepsPreviousReportOnReanalysis() {
        User user = saveUser("reanalysis@example.com");
        Long first = analysisReportStore.createPending(user.getId());
        analysisReportStore.complete(first, response(stack("Java", 80, "INTERMEDIATE", "설명")));

        Long second = analysisReportStore.createPending(user.getId());
        analysisReportStore.complete(second, response(stack("Kotlin", 90, "ADVANCED", "설명")));

        assertThat(second).isNotEqualTo(first);
        // 지난 리포트를 다시 열었을 때 그때 무엇을 할 줄 알았는지 보여야 한다.
        assertThat(analysisReportStore.findStacks(first))
                .extracting(stack -> stack.getStackDetail().getStackName())
                .containsExactly("Java");
        assertThat(analysisReportStore.findStacks(second))
                .extracting(stack -> stack.getStackDetail().getStackName())
                .containsExactly("Kotlin");
        assertThat(analysisReportStore.findLatest(user.getId()))
                .hasValueSatisfying(report -> assertThat(report.getId()).isEqualTo(second));
    }

    @Test
    @DisplayName("기술 이름 목록은 사용자끼리 나눠 쓴다")
    void sharesStackCatalogBetweenUsers() {
        User first = saveUser("first@example.com");
        User second = saveUser("second@example.com");
        Long firstReport = analysisReportStore.createPending(first.getId());
        Long secondReport = analysisReportStore.createPending(second.getId());

        analysisReportStore.complete(firstReport, response(stack("Java", 80, "INTERMEDIATE", "설명")));
        analysisReportStore.complete(secondReport, response(stack("Java", 60, "BEGINNER", "설명")));

        assertThat(stackDetailRepository.findAll())
                .filteredOn(detail -> "Java".equals(detail.getStackName()))
                .hasSize(1);
    }

    @Test
    @DisplayName("직무의 축 순서대로 축별 점수를 저장하고, 축의 이름도 함께 남긴다")
    void savesCategoryScores() {
        User user = saveUser("category@example.com");
        changeDesiredJob(user, DesiredJob.BACKEND);
        Long reportId = analysisReportStore.createPending(user.getId());

        // AI 가 축 순서를 섞어 보내도 축 정의의 순서로 저장한다. 레이더 차트의 축이 리포트마다 흔들리면 안 된다.
        analysisReportStore.complete(reportId, response(List.of(
                new CategoryScore("MONITORING", 40, "로그 수집 경험이 없다"),
                new CategoryScore("DATA_MODELING", 70, "인덱스를 설계했다"))));

        List<AnalysisReportCategory> categories = analysisReportStore.findCategories(reportId);
        assertThat(categories)
                .extracting(AnalysisReportCategory::getCode, AnalysisReportCategory::getScore)
                .containsExactly(tuple("DATA_MODELING", 70), tuple("MONITORING", 40));
        assertThat(categories.get(0).getName()).isEqualTo("효율적인 데이터 설계 및 최적화");
        assertThat(categories.get(0).getInterpretation()).isEqualTo("인덱스를 설계했다");
    }

    @Test
    @DisplayName("요청하지 않은 축은 버리고, 빠진 축을 0점으로 채우지 않는다")
    void ignoresUnknownAndMissingCategories() {
        User user = saveUser("unknown-category@example.com");
        changeDesiredJob(user, DesiredJob.BACKEND);
        Long reportId = analysisReportStore.createPending(user.getId());

        analysisReportStore.complete(reportId, response(List.of(
                new CategoryScore("FRONTEND", 90, "풀스택의 축이다"),
                new CategoryScore("ARCHITECTURE", 60, "계층을 나눴다"))));

        // 0점은 "못한다"로 읽힌다. 판단하지 않은 축은 아예 없어야 한다.
        assertThat(analysisReportStore.findCategories(reportId))
                .extracting(AnalysisReportCategory::getCode)
                .containsExactly("ARCHITECTURE");
    }

    @Test
    @DisplayName("축을 정하지 않은 직무면 축 점수를 받아도 저장하지 않는다")
    void ignoresCategoriesForJobWithoutCategories() {
        User user = saveUser("frontend@example.com");
        changeDesiredJob(user, DesiredJob.FRONTEND);
        Long reportId = analysisReportStore.createPending(user.getId());

        analysisReportStore.complete(reportId, response(
                List.of(new CategoryScore("ARCHITECTURE", 60, "설명")),
                new AnalyzedStack("Java", 80, "INTERMEDIATE", "설명", "ARCHITECTURE", List.of("GITHUB"))));

        assertThat(analysisReportStore.findCategories(reportId)).isEmpty();
        assertThat(analysisReportStore.findStacks(reportId))
                .singleElement()
                .satisfies(stack -> assertThat(stack.getCategoryCode()).isNull());
    }

    @Test
    @DisplayName("기술마다 속한 축과 근거를 찾은 곳을 저장한다")
    void savesStackCategoryAndSources() {
        User user = saveUser("stack-category@example.com");
        changeDesiredJob(user, DesiredJob.BACKEND);
        Long reportId = analysisReportStore.createPending(user.getId());

        analysisReportStore.complete(reportId, response(List.of(),
                new AnalyzedStack("Java", 80, "INTERMEDIATE", "설명", "ARCHITECTURE", List.of("GITHUB", "PORTFOLIO")),
                new AnalyzedStack("Spring Boot", 50, "BEGINNER", "설명", "FRONTEND", List.of("PORTFOLIO"))));

        assertThat(analysisReportStore.findStacks(reportId))
                .filteredOn(stack -> stack.getStackDetail().getStackName().equals("Java"))
                .singleElement()
                .satisfies(stack -> {
                    assertThat(stack.getCategoryCode()).isEqualTo("ARCHITECTURE");
                    assertThat(stack.isFoundInGithub()).isTrue();
                    assertThat(stack.isFoundInPortfolio()).isTrue();
                });
        // 이 직무의 축이 아닌 code 에는 기술을 매달지 않는다.
        assertThat(analysisReportStore.findStacks(reportId))
                .filteredOn(stack -> stack.getStackDetail().getStackName().equals("Spring Boot"))
                .singleElement()
                .satisfies(stack -> {
                    assertThat(stack.getCategoryCode()).isNull();
                    assertThat(stack.isFoundInGithub()).isFalse();
                    assertThat(stack.isFoundInPortfolio()).isTrue();
                });
    }

    @Test
    @DisplayName("분석이 실패하면 그 리포트에 이유를 남긴다")
    void recordsFailure() {
        User user = saveUser("failure@example.com");
        Long reportId = analysisReportStore.createPending(user.getId());

        analysisReportStore.fail(reportId, "RestClientException: 503");

        AnalysisReport report = analysisReportRepository.findById(reportId).orElseThrow();
        assertThat(report.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(report.getFailureReason()).contains("503");
        assertThat(analysisReportStore.isAnalyzing(user.getId())).isFalse();
    }

    private AnalysisAiResponse response(AnalyzedStack... stacks) {
        return response(List.of(), stacks);
    }

    private AnalysisAiResponse response(List<CategoryScore> categories, AnalyzedStack... stacks) {
        return new AnalysisAiResponse(
                "백엔드 주니어", 72, "요약", "깃허브 분석", "포트폴리오 분석",
                List.of(stacks),
                categories,
                new AnalysisAiResponse.Sources(12, true, List.of()));
    }

    /** 축과 출처를 따지지 않는 테스트에서 쓴다. */
    private AnalyzedStack stack(String name, int score, String level, String description) {
        return new AnalyzedStack(name, score, level, description, null, List.of());
    }

    private void changeDesiredJob(User user, DesiredJob desiredJob) {
        user.completeProfile("분석유저", 27, ExperienceLevel.JUNIOR, desiredJob,
                "portfolio/1/portfolio.pdf", "portfolio.pdf", LocalDateTime.now());
        userRepository.save(user);
    }

    private void clear() {
        analysisReportCategoryRepository.deleteAll();
        userStackRepository.deleteAll();
        analysisReportRepository.deleteAll();
        stackDetailRepository.deleteAll();
    }

    private User saveUser(String email) {
        User saved = userRepository.save(
                User.builder()
                        .email(email)
                        .password("encoded-password")
                        .socialType(SocialType.LOCAL)
                        .nickname("분석유저")
                        .username("분석유저")
                        .role(Role.USER)
                        .build()
        );
        createdUsers.add(saved);
        return saved;
    }
}
