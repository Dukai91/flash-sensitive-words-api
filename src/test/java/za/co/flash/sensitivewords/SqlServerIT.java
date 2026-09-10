package za.co.flash.sensitivewords;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import za.co.flash.sensitivewords.application.SensitiveWordService;
import za.co.flash.sensitivewords.exception.DuplicateWordException;
import za.co.flash.sensitivewords.matcher.MatcherCache;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class SqlServerIT {
    @Container
    static final MSSQLServerContainer<?> SQL = new MSSQLServerContainer<>(
            "mcr.microsoft.com/mssql/server:2022-CU26-ubuntu-22.04").acceptLicense();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL::getJdbcUrl);
        registry.add("spring.datasource.username", SQL::getUsername);
        registry.add("spring.datasource.password", SQL::getPassword);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private SensitiveWordService service;
    @Autowired private MatcherCache cache;
    private static final String WORDS = "/api/v1/internal/sensitive-words";

    @Test
    void flywayAndHibernateStartAgainstRealSqlServerWithExactSeed() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT word FROM sensitive_words ORDER BY id", String.class))
                .containsExactlyElementsOf(SeedSourceTest.suppliedWords());
        mvc.perform(post("/api/v1/sanitize").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"SELECT * FROM sensitiveWords\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sanitized").value("****** * FROM sensitiveWords"));
    }

    @Test
    void fullCrudPersistsAndRefreshesMatcher() throws Exception {
        String created = mvc.perform(post(WORDS).contentType(MediaType.APPLICATION_JSON).content("{\"word\":\"  confidential  \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.word").value("confidential"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();
        try {
            assertThat(cache.sanitize("confidential")).isEqualTo("************");
            mvc.perform(get(WORDS + "/" + id)).andExpect(status().isOk());
            mvc.perform(get(WORDS).param("page", "0").param("size", "10"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(229));
            mvc.perform(post(WORDS).contentType(MediaType.APPLICATION_JSON).content("{\"word\":\"CONFIDENTIAL\"}"))
                    .andExpect(status().isConflict());
            mvc.perform(put(WORDS + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"word\":\"CREATE\"}"))
                    .andExpect(status().isConflict());
            mvc.perform(put(WORDS + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"word\":\"classified\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.updatedAt").isNotEmpty());
            assertThat(cache.sanitize("confidential classified")).isEqualTo("confidential **********");
            assertThat(jdbc.queryForObject("SELECT normalized_word FROM sensitive_words WHERE id=?", String.class, id))
                    .isEqualTo("classified");
        } finally {
            mvc.perform(delete(WORDS + "/" + id)).andExpect(status().isNoContent());
        }
        assertThat(cache.sanitize("classified")).isEqualTo("classified");
        mvc.perform(get(WORDS + "/" + id)).andExpect(status().isNotFound());
        mvc.perform(delete(WORDS + "/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void databaseEnforcesUniquenessWithoutApplicationPrecheck() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO sensitive_words(word,normalized_word) VALUES(?,?)", "Create", "create"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO sensitive_words(word,normalized_word) VALUES(?,?)", " ", " "))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void simultaneousCreatesDoNotLoseCommittedMatcherEntries() throws Exception {
        List<String> terms = List.of("alphaunique", "betaunique", "gammaunique", "deltaunique");
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = terms.stream().<Callable<Long>>map(term -> () -> service.create(term).id()).toList();
            var results = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
            try {
                for (String term : terms) {
                    assertThat(cache.sanitize(term)).isEqualTo("*".repeat(term.length()));
                }
                for (var result : results) {
                    assertThat(result.get()).isPositive();
                }
            } finally {
                for (var result : results) {
                    if (!result.isCancelled()) {
                        service.delete(result.get());
                    }
                }
            }
        }
    }

    @Test
    void swaggerAndHealthAreAvailableAndDescribeEveryRoute() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        var spec = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var paths = json.readTree(spec).get("paths");
        assertThat(paths.get("/api/v1/sanitize").get("post").get("responses").has("200")).isTrue();
        assertThat(paths.get(WORDS).get("post").get("responses").has("409")).isTrue();
        assertThat(paths.get(WORDS).has("get")).isTrue();
        assertThat(paths.get(WORDS + "/{id}").has("get")).isTrue();
        assertThat(paths.get(WORDS + "/{id}").has("put")).isTrue();
        assertThat(paths.get(WORDS + "/{id}").has("delete")).isTrue();
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }
}
