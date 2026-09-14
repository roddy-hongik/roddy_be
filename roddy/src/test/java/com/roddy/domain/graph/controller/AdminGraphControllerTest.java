package com.roddy.domain.graph.controller;

import com.jayway.jsonpath.JsonPath;
import com.roddy.domain.auth.entity.User;
import com.roddy.domain.auth.repository.UserRepository;
import com.roddy.domain.auth.service.SocialAuthService;
import com.roddy.domain.enums.DesiredJob;
import com.roddy.domain.enums.Role;
import com.roddy.domain.enums.SocialType;
import com.roddy.domain.jobposting.dto.JobPostingSnapshot;
import com.roddy.domain.jobposting.entity.JobPosting;
import com.roddy.domain.jobposting.repository.JobPostingRepository;
import com.roddy.global.config.s3.S3Uploader;
import com.roddy.global.security.UserDetailsImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.neo4j.Neo4jContainer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 실제 Neo4j 를 띄워 기술 그래프를 확인한다. Docker 가 없으면 건너뛴다. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AdminGraphControllerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);

    @Container
    @ServiceConnection
    static final Neo4jContainer NEO4J = new Neo4jContainer("neo4j:5-community").withoutAuthentication();

    @Autowired private WebApplicationContext context;
    @Autowired private Driver driver;
    @Autowired private JobPostingRepository jobPostingRepository;
    @Autowired private UserRepository userRepository;

    @MockitoBean private S3Uploader s3Uploader;
    @MockitoBean private SocialAuthService socialAuthService;

    private MockMvc mockMvc;
    private User admin;
    private final List<JobPosting> postings = new ArrayList<>();
    private final List<User> users = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        driver.executableQuery("MATCH (n) DETACH DELETE n").execute();
        admin = saveUser("graph-admin@example.com", Role.ADMIN);
    }

    @AfterEach
    void tearDown() {
        jobPostingRepository.deleteAll(postings);
        postings.clear();
        userRepository.deleteAll(users);
        users.clear();
    }

    @Test
    void 사전의_기술을_노드로_두고_모집_중인_공고로_함께_요구되는_기술을_잇는다() throws Exception {
        savePostings(3, false, "Java", "Spring", "QueryDSL");
        savePostings(3, true, "Java", "Kafka");

        rebuild()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.technologyStackCount").value(96))
                .andExpect(jsonPath("$.result.autoRelationCount").value(3));

        mockMvc.perform(get("/api/admin/graph/search").param("keyword", "자바").with(user(new UserDetailsImpl(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.searchedNode.name").value("Java"))
                .andExpect(jsonPath("$.result.searchedNode.category").value("language"))
                .andExpect(jsonPath("$.result.searchedNode.relationCount").value(2))
                .andExpect(jsonPath("$.result.edges.length()").value(2))
                .andExpect(jsonPath("$.result.edges[0].relationType").value("USED_WITH"))
                .andExpect(jsonPath("$.result.edges[0].createdBy").value("auto"))
                .andExpect(jsonPath("$.result.edges[0].confidence").value(1.0));

        // 마감된 공고에서만 함께 나온 기술은 잇지 않는다.
        search("Kafka")
                .andExpect(jsonPath("$.result.searchedNode.category").value("data"))
                .andExpect(jsonPath("$.result.searchedNode.relationCount").value(0));

        search("없는기술")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.searchedNode").doesNotExist())
                .andExpect(jsonPath("$.result.edges.length()").value(0));
    }

    @Test
    void 어드민이_지운_자동_관계는_다시_계산해도_되살아나지_않는다() throws Exception {
        savePostings(3, false, "Java", "Spring", "QueryDSL");
        rebuild();

        String queryDslEdgeId = edgeId("Java", "QueryDSL");
        mockMvc.perform(delete("/api/admin/graph/edges/{edgeId}", queryDslEdgeId).with(user(new UserDetailsImpl(admin))))
                .andExpect(status().isOk());

        rebuild().andExpect(jsonPath("$.result.autoRelationCount").value(2));

        search("Java")
                .andExpect(jsonPath("$.result.searchedNode.relationCount").value(1))
                .andExpect(jsonPath("$.result.edges[0].target").value("Spring"));
    }

    @Test
    void 어드민이_만들거나_고친_관계는_자동_계산이_덮어쓰지_않는다() throws Exception {
        savePostings(3, false, "Java", "Spring", "QueryDSL");
        rebuild();

        mockMvc.perform(post("/api/admin/graph/edges")
                        .with(user(new UserDetailsImpl(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source": "자바", "relationType": "PREREQUISITE_OF", "target": "spring boot", "description": "자바를 먼저 익힌다"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.source").value("Java"))
                .andExpect(jsonPath("$.result.target").value("Spring Boot"))
                .andExpect(jsonPath("$.result.createdBy").value("manual"))
                .andExpect(jsonPath("$.result.confidence").value(1.0));

        String springEdgeId = edgeId("Java", "Spring");
        mockMvc.perform(put("/api/admin/graph/edges/{edgeId}", springEdgeId)
                        .with(user(new UserDetailsImpl(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"source": "Java", "relationType": "USED_WITH", "target": "Spring", "description": "함께 쓰는 조합"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(springEdgeId))
                .andExpect(jsonPath("$.result.createdBy").value("manual"))
                .andExpect(jsonPath("$.result.description").value("함께 쓰는 조합"));

        rebuild();

        String body = search("Java").andReturn().getResponse().getContentAsString();
        List<String> javaSpring = JsonPath.read(body,
                "$.result.edges[?((@.source == 'Java' && @.target == 'Spring') || (@.source == 'Spring' && @.target == 'Java'))].createdBy");
        List<String> prerequisites = JsonPath.read(body, "$.result.edges[?(@.relationType == 'PREREQUISITE_OF')].target");
        assertThat(javaSpring).containsExactly("manual");
        assertThat(prerequisites).containsExactly("Spring Boot");
    }

    @Test
    void 잘못된_관계는_만들지_않는다() throws Exception {
        rebuild();

        createEdge("Java", "USED_WITH", "없는기술")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GRAPH_4041"));
        createEdge("Java", "RELATED_TO", "자바")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GRAPH_4001"));
        createEdge("Java", "SIMILAR_TO", "Kotlin").andExpect(status().isOk());
        createEdge("Kotlin", "SIMILAR_TO", "Java")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GRAPH_4091"));
        mockMvc.perform(delete("/api/admin/graph/edges/{edgeId}", "없는-관계").with(user(new UserDetailsImpl(admin))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GRAPH_4042"));
    }

    @Test
    void 어드민이_아니면_그래프를_관리할_수_없다() throws Exception {
        User member = saveUser("graph-member@example.com", Role.USER);

        mockMvc.perform(get("/api/admin/graph/search").param("keyword", "Java").with(user(new UserDetailsImpl(member))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/graph/rebuild").with(user(new UserDetailsImpl(member))))
                .andExpect(status().isForbidden());
    }

    private ResultActions rebuild() throws Exception {
        return mockMvc.perform(post("/api/admin/graph/rebuild").with(user(new UserDetailsImpl(admin))));
    }

    private ResultActions search(String keyword) throws Exception {
        return mockMvc.perform(get("/api/admin/graph/search").param("keyword", keyword).with(user(new UserDetailsImpl(admin))));
    }

    private ResultActions createEdge(String source, String relationType, String target) throws Exception {
        return mockMvc.perform(post("/api/admin/graph/edges")
                .with(user(new UserDetailsImpl(admin)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\": \"%s\", \"relationType\": \"%s\", \"target\": \"%s\"}".formatted(source, relationType, target)));
    }

    private String edgeId(String name, String other) throws Exception {
        String body = search(name).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body,
                "$.result.edges[?(@.source == '%s' || @.target == '%s')].id".formatted(other, other));
        assertThat(ids).hasSize(1);
        return ids.getFirst();
    }

    private void savePostings(int count, boolean closed, String... stacks) {
        for (int i = 0; i < count; i++) {
            String externalId = "graph-%s-%d-%d".formatted(String.join("-", stacks), closed ? 1 : 0, i);
            JobPosting posting = JobPosting.create(JobPostingSnapshot.builder()
                    .companyCode("graph")
                    .externalId(externalId)
                    .company("그래프")
                    .title("백엔드 개발자")
                    .desiredJob(DesiredJob.BACKEND)
                    .applyUrl("https://example.com/" + externalId)
                    .build(), NOW);
            posting.updateTechStacks(Set.of(stacks));
            if (closed) {
                posting.close(NOW);
            }
            postings.add(jobPostingRepository.save(posting));
        }
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
