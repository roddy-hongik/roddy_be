package com.roddy.domain.coverletter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.coverletter.repository.CoverLetterRepository;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.domain.jobposting.dto.JobPostingSnapshot;
import com.roddy.domain.jobposting.entity.JobPosting;
import com.roddy.domain.jobposting.repository.JobPostingRepository;
import com.roddy.global.config.s3.S3Uploader;
import com.roddy.global.security.UserDetailsImpl;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CoverLetterControllerTest {
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired JobPostingRepository jobs;
    @Autowired CoverLetterRepository letters;
    @MockitoBean S3Uploader uploader;
    @MockitoBean SocialAuthService social;
    MockMvc mvc;
    User owner;
    User other;
    ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        owner = saveUser(); other = saveUser();
    }

    @Test void 문서와_문항을_저장하고_교체하고_삭제한다() throws Exception {
        long id = create("초안");
        entityManager.clear();
        mvc.perform(get("/api/cover-letters/" + id).with(user(new UserDetailsImpl(owner))))
                .andExpect(jsonPath("$.result.answers[0].answer").value(""));
        mvc.perform(put("/api/cover-letters/" + id).with(user(new UserDetailsImpl(owner)))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"수정본","version":0,"answers":[
                  {"question":"두 번째 문항","answer":"줄1\\n줄2"},
                  {"question":"첫 번째 문항","answer":"답변"}]}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.version").value(1))
                .andExpect(jsonPath("$.result.answers[0].question").value("두 번째 문항"))
                .andExpect(jsonPath("$.result.answers[0].answer").value("줄1\n줄2"));
        entityManager.clear();
        mvc.perform(get("/api/cover-letters/" + id).with(user(new UserDetailsImpl(owner))))
                .andExpect(jsonPath("$.result.answers.length()").value(2));
        mvc.perform(delete("/api/cover-letters/" + id).param("version", "1").with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk());
        entityManager.clear();
        assertThat(letters.existsById(id)).isFalse();
        assertThat(((Number) entityManager.createNativeQuery("select count(*) from cover_letter_answers where cover_letter_id = :id")
                .setParameter("id", id).getSingleResult()).longValue()).isZero();
    }

    @Test void 다른_사용자는_목록_상세_수정_삭제에_접근하지_못한다() throws Exception {
        long id = create("비공개");
        var stranger = user(new UserDetailsImpl(other));
        mvc.perform(get("/api/cover-letters").with(stranger))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.totalElements").value(0));
        mvc.perform(get("/api/cover-letters/" + id).with(stranger)).andExpect(status().isNotFound());
        mvc.perform(put("/api/cover-letters/" + id).with(stranger).contentType(MediaType.APPLICATION_JSON)
                .content(body("탈취"))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/cover-letters/" + id).param("version", "0").with(stranger))
                .andExpect(status().isNotFound());
        assertThat(letters.existsById(id)).isTrue();
    }

    @Test void 오래된_버전은_수정과_삭제를_거부한다() throws Exception {
        long id = create("최신");
        mvc.perform(put("/api/cover-letters/" + id).with(user(new UserDetailsImpl(owner)))
                .contentType(MediaType.APPLICATION_JSON).content(body("수정")))
                .andExpect(status().isOk());
        mvc.perform(put("/api/cover-letters/" + id).with(user(new UserDetailsImpl(owner)))
                .contentType(MediaType.APPLICATION_JSON).content(body("오래된 수정")))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/cover-letters/" + id).param("version", "0").with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isConflict());
    }

    @Test void 문항만_수정해도_버전이_증가한다() throws Exception {
        long id = create("초안");
        mvc.perform(put("/api/cover-letters/" + id).with(user(new UserDetailsImpl(owner)))
                .contentType(MediaType.APPLICATION_JSON).content(body("초안")))
                .andExpect(status().isOk());
        mvc.perform(put("/api/cover-letters/" + id).with(user(new UserDetailsImpl(owner)))
                .contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"초안","version":1,"answers":[{"question":"지원 동기","answer":"변경"}]}
                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.version").value(2));
    }

    @Test void 공고를_연결하고_해제할_수_있다() throws Exception {
        JobPosting job = jobs.saveAndFlush(JobPosting.create(JobPostingSnapshot.builder()
                .companyCode("test").externalId(UUID.randomUUID().toString()).company("기업")
                .title("개발자").applyUrl("https://example.com/jobs").build(), LocalDateTime.now()));
        String linked = body("공고 지원").replace("\"version\":0", "\"version\":0,\"jobPostingId\":" + job.getId());
        String result = mvc.perform(post("/api/cover-letters").with(user(new UserDetailsImpl(owner)))
                .contentType(MediaType.APPLICATION_JSON).content(linked)).andExpect(status().isOk())
                .andExpect(jsonPath("$.result.job.company").value("기업")).andReturn().getResponse().getContentAsString();
        long id = json.readTree(result).path("result").path("id").asLong();
        mvc.perform(put("/api/cover-letters/" + id).with(user(new UserDetailsImpl(owner)))
                .contentType(MediaType.APPLICATION_JSON).content(body("공고 해제")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.job").isEmpty());
    }

    @Test void 잘못된_입력은_저장하지_않는다() throws Exception {
        for (String invalid : new String[]{
                "{\"title\":\" \",\"answers\":[]}",
                "{\"title\":\"초안\",\"answers\":[null]}",
                "{\"title\":\"초안\",\"answers\":[{\"question\":\"질문\",\"answer\":null}]}"}) {
            mvc.perform(post("/api/cover-letters").with(user(new UserDetailsImpl(owner)))
                    .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/cover-letters").with(user(new UserDetailsImpl(owner)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("초안").replace("\"version\":0", "\"jobPostingId\":9223372036854775807")))
                .andExpect(status().isNotFound());
    }

    @Test void 목록은_페이지로_조회하고_본문은_싣지_않는다() throws Exception {
        create("첫째"); create("둘째");
        mvc.perform(get("/api/cover-letters").param("size", "1").with(user(new UserDetailsImpl(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.coverLetters.length()").value(1))
                .andExpect(jsonPath("$.result.coverLetters[0].answers").doesNotExist());
    }

    @Test void 비로그인_접근은_거부한다() throws Exception {
        mvc.perform(get("/api/cover-letters")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cover-letters/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/cover-letters").contentType(MediaType.APPLICATION_JSON).content(body("초안")))
                .andExpect(status().isUnauthorized());
    }

    long create(String title) throws Exception {
        String response = mvc.perform(post("/api/cover-letters").with(user(new UserDetailsImpl(owner)))
                .contentType(MediaType.APPLICATION_JSON).content(body(title)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("result").path("id").asLong();
    }
    String body(String title) {
        return "{\"title\":\"" + title + "\",\"version\":0,\"answers\":[{\"question\":\"지원 동기\",\"answer\":\"\"}]}";
    }
    User saveUser() {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                .password("encoded").nickname("작성자").username("작성자")
                .socialType(SocialType.LOCAL).role(Role.USER).build());
    }
}
