package com.iflytek.skillhub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.demand.DemandService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class DemandControllerTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("skillhub.builtin-skills.enabled", () -> "false");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DemandService service;
    private String author;
    private String colleague;
    private String moderator;
    private String marker;

    @BeforeEach
    void users() {
        marker = UUID.randomUUID().toString();
        author = "author-" + marker;
        colleague = "colleague-" + marker;
        moderator = "moderator-" + marker;
        for (String id : List.of(author, colleague, moderator)) {
            jdbc.update("INSERT INTO user_account(id, display_name, status, system_account, created_at, updated_at) VALUES (?, ?, 'ACTIVE', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", id, id);
        }
    }

    @Test
    void employeesCanPublishSupportAndContributeWithoutChangingEachOthersContent() throws Exception {
        long id = create("Process receipts");
        mvc.perform(put("/api/web/demands/{id}/support", id).with(as(colleague)).with(csrf())).andExpect(status().isOk());
        mvc.perform(put("/api/web/demands/{id}/support", id).with(as(colleague)).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/web/demands/{id}", id).with(as(colleague)))
                .andExpect(jsonPath("$.data.supportCount").value(1)).andExpect(jsonPath("$.data.supported").value(true));
        long supplementId = json.readTree(mvc.perform(post("/api/web/demands/{id}/supplements", id)
                .with(as(colleague)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Include returns too\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").asLong();
        mvc.perform(get("/api/web/demands/{id}", id).with(as(author)))
                .andExpect(jsonPath("$.data.supportCount").value(1)).andExpect(jsonPath("$.data.supplementCount").value(1));
        mvc.perform(put("/api/web/demands/{id}", id).with(as(colleague)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(payload("Unauthorized"))).andExpect(status().isForbidden());
        mvc.perform(put("/api/web/demands/{id}/supplements/{sid}", id, supplementId).with(as(author)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Unauthorized\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/web/demands/{id}/supplements/{sid}", id, supplementId).with(as(colleague)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Updated scenario\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/web/demands/{id}/supplements", id).with(as(author)))
                .andExpect(jsonPath("$.data.items[0].content").value("Updated scenario"));
        mvc.perform(delete("/api/web/demands/{id}/supplements/{sid}", id, supplementId).with(as(author)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/web/demands/{id}/supplements/{sid}", id, supplementId).with(as(colleague)).with(csrf())).andExpect(status().isOk());
        mvc.perform(delete("/api/web/demands/{id}/support", id).with(as(colleague)).with(csrf())).andExpect(status().isOk());
        mvc.perform(delete("/api/web/demands/{id}/support", id).with(as(colleague)).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/web/demands/{id}", id).with(as(author)))
                .andExpect(jsonPath("$.data.supportCount").value(0)).andExpect(jsonPath("$.data.supplementCount").value(0));
    }

    @Test
    void concurrentSupportRetriesCountOnlyOnce() throws Exception {
        long id = create("Concurrent demand");
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<Callable<Void>> tasks = IntStream.range(0, 12).mapToObj(i -> (Callable<Void>) () -> {
                service.support(id, colleague, true);
                return null;
            }).toList();
            for (var result : executor.invokeAll(tasks)) result.get();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM demand_support WHERE demand_id=?", Long.class, id)).isEqualTo(1L);
    }

    @Test
    void hiddenContentIsNotReadableOrMutableByEmployeesEvenUsingDirectUrls() throws Exception {
        long id = create("Hidden demand");
        mvc.perform(put("/api/web/demands/{id}/visibility", id).param("hidden", "true").with(as(author)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put("/api/web/demands/{id}/visibility", id).param("hidden", "true").with(as(moderator)).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/web/demands/{id}", id).with(as(author))).andExpect(status().isNotFound());
        mvc.perform(get("/api/web/demands/{id}/supplements", id).with(as(colleague))).andExpect(status().isNotFound());
        mvc.perform(put("/api/web/demands/{id}/support", id).with(as(colleague)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(get("/api/web/demands").param("q", marker).param("includeHidden", "true").with(as(author)))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/web/demands").param("q", marker).param("includeHidden", "true").with(as(moderator)))
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(put("/api/web/demands/{id}/visibility", id).param("hidden", "false").with(as(moderator)).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/web/demands/{id}", id).with(as(author))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE target_id=? AND action='DEMAND_HIDE'", Long.class, id)).isEqualTo(1L);
    }

    @Test
    void filtersSortingAndPaginationUseActualSupportCounts() throws Exception {
        long first = create("First");
        long second = create("Second");
        service.support(first, colleague, true);
        mvc.perform(get("/api/web/demands").with(as(author)).param("q", marker).param("sort", "newest").param("size", "1"))
                .andExpect(jsonPath("$.data.total").value(2)).andExpect(jsonPath("$.data.items[0].id").value(second));
        mvc.perform(get("/api/web/demands").with(as(author)).param("q", marker).param("sort", "popular").param("size", "1"))
                .andExpect(jsonPath("$.data.items[0].id").value(first));
        mvc.perform(get("/api/web/demands").with(as(colleague)).param("q", marker).param("mine", "true"))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/web/demands").with(as(author)).param("q", marker).param("category", "missing"))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/web/demands").with(as(author)).param("q", marker).param("sort", "popular").param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.data.items[0].id").value(second));
    }

    @Test
    void authenticationCsrfAndInputBoundsAreEnforced() throws Exception {
        mvc.perform(get("/api/web/demands")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/demands/123")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/web/demands").with(as(author)).contentType(MediaType.APPLICATION_JSON).content(payload("Missing CSRF")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/web/demands").with(as(author)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\" \",\"scenario\":\"x\",\"expectedResult\":\"y\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/web/demands").with(as(author)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(payload("a".repeat(121)))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/web/demands").with(as(author)).param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/web/demands").with(as(author)).param("sort", "invalid")).andExpect(status().isBadRequest());
    }

    @Test
    void supplementsStayBoundToTheirDemandAndRespectModeration() throws Exception {
        long first = create("First parent");
        long second = create("Second parent");
        long supplement = service.supplement(first, colleague, "Additional scenario");
        mvc.perform(put("/api/web/demands/{id}/supplements/{sid}", second, supplement).with(as(colleague)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Wrong parent\"}")).andExpect(status().isNotFound());
        mvc.perform(put("/api/web/demands/{id}/supplements/{sid}/visibility", first, supplement)
                .param("hidden", "true").with(as(moderator)).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/web/demands/{id}/supplements", first).with(as(colleague)))
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/web/demands/{id}/supplements", first).with(as(moderator)))
                .andExpect(jsonPath("$.data.items[0].hidden").value(true));
        mvc.perform(put("/api/web/demands/{id}/supplements/{sid}", first, supplement).with(as(colleague)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Cannot edit hidden content\"}")).andExpect(status().isNotFound());
        mvc.perform(put("/api/web/demands/{id}/supplements/{sid}/visibility", first, supplement)
                .param("hidden", "false").with(as(moderator)).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/web/demands/{id}/supplements", first).with(as(colleague)))
                .andExpect(jsonPath("$.data.items[0].content").value("Additional scenario"));
    }

    @Test
    void keywordWildcardsAreLiteralAndAuthorsCanEditTheirDemand() throws Exception {
        long first = create("100% coverage");
        create("Other request");
        mvc.perform(get("/api/web/demands").with(as(author)).param("q", "%").param("mine", "true"))
                .andExpect(jsonPath("$.data.total").value(1)).andExpect(jsonPath("$.data.items[0].id").value(first));
        mvc.perform(put("/api/web/demands/{id}", first).with(as(author)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(payload("Updated request"))).andExpect(status().isOk());
        mvc.perform(get("/api/web/demands/{id}", first).with(as(colleague)))
                .andExpect(jsonPath("$.data.title").value("Updated request"));
    }

    private long create(String title) throws Exception {
        return json.readTree(mvc.perform(post("/api/web/demands").with(as(author)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(payload(title)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data").asLong();
    }

    private String payload(String title) throws Exception {
        return json.writeValueAsString(java.util.Map.of("title", title, "scenario", marker,
                "expectedResult", "A reusable result", "category", "Operations"));
    }

    private RequestPostProcessor as(String userId) {
        boolean admin = userId.equals(moderator);
        var principal = new PlatformPrincipal(userId, userId, null, null, "local", admin ? Set.of("SUPER_ADMIN") : Set.of());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null,
                admin ? List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")) : List.of()));
    }
}
