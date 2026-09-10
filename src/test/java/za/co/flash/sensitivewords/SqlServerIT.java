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
import org.junit.jupiter.api.Timeout;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.flash.sensitivewords.persistence.SensitiveWordRepository;
import za.co.flash.sensitivewords.exception.InvalidInputException;
import za.co.flash.sensitivewords.exception.DuplicateWordException;
import static org.mockito.Mockito.*;
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
        try (var connection = java.sql.DriverManager.getConnection(SQL.getJdbcUrl(), SQL.getUsername(), SQL.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("IF DB_ID(N'sensitive_words_test') IS NULL CREATE DATABASE sensitive_words_test");
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Cannot initialize test database", exception);
        }
        registry.add("spring.datasource.url", () -> "jdbc:sqlserver://" + SQL.getHost() + ":" + SQL.getMappedPort(1433)
                + ";databaseName=sensitive_words_test;encrypt=true;trustServerCertificate=true");
        registry.add("spring.datasource.username", SQL::getUsername);
        registry.add("spring.datasource.password", SQL::getPassword);
        registry.add("vocabulary.refresh-delay-ms", () -> "3600000");
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private SensitiveWordService service;
    @Autowired private MatcherCache cache;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoSpyBean private SensitiveWordRepository repository;
    private static final String WORDS = "/api/v1/internal/sensitive-words";

    @Test
    void flywayAndHibernateStartAgainstRealSqlServerWithExactSeed() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT DB_NAME()", String.class)).isEqualTo("sensitive_words_test");
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
            String read = mvc.perform(get(WORDS + "/" + id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(json.readTree(read).get("createdAt")).isEqualTo(json.readTree(created).get("createdAt"));
            assertThat(json.readTree(read).get("updatedAt")).isEqualTo(json.readTree(created).get("updatedAt"));
            mvc.perform(get(WORDS).param("page", "0").param("size", "10"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(229));
            mvc.perform(post(WORDS).contentType(MediaType.APPLICATION_JSON).content("{\"word\":\"CONFIDENTIAL\"}"))
                    .andExpect(status().isConflict());
            mvc.perform(put(WORDS + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"word\":\"CREATE\"}"))
                    .andExpect(status().isConflict());
            String updated = mvc.perform(put(WORDS + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"word\":\"classified\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(json.readTree(updated).get("createdAt")).isEqualTo(json.readTree(created).get("createdAt"));
            assertThat(java.time.Instant.parse(json.readTree(updated).get("updatedAt").asText()))
                    .isAfter(java.time.Instant.parse(json.readTree(created).get("updatedAt").asText()));
            String reread = mvc.perform(get(WORDS + "/" + id)).andReturn().getResponse().getContentAsString();
            assertThat(json.readTree(reread)).isEqualTo(json.readTree(updated));
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
    void flushedWriteAndRevisionRollBackWhenMatcherBuildFails() {
        long revision = cache.revision();
        doThrow(new InvalidInputException("forced compilation failure")).when(repository).findAllWords();
        try {
            assertThatThrownBy(() -> service.create("rollbackunique")).isInstanceOf(InvalidInputException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sensitive_words WHERE word='rollbackunique'", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT revision FROM vocabulary_configuration WHERE id=1", Long.class)).isEqualTo(revision);
            assertThat(cache.sanitize("CREATE rollbackunique")).isEqualTo("****** rollbackunique");
        } finally {
            reset(repository);
        }
    }

    @Test
    void reconcilesAnActuallyCommittedWriteWhoseAcknowledgementWasLost() {
        var uncertainCache = new MatcherCache();
        var failNextCommit = new java.util.concurrent.atomic.AtomicBoolean(false);
        PlatformTransactionManager uncertainManager = new PlatformTransactionManager() {
            @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
                return transactionManager.getTransaction(definition);
            }
            @Override public void commit(TransactionStatus status) {
                transactionManager.commit(status);
                if (failNextCommit.getAndSet(false)) throw new TransactionSystemException("Simulated lost acknowledgement");
            }
            @Override public void rollback(TransactionStatus status) { transactionManager.rollback(status); }
        };
        var instance = new SensitiveWordService(repository, uncertainCache, uncertainManager);
        instance.run(null);
        failNextCommit.set(true);
        try {
            assertThatThrownBy(() -> instance.create("acknowledgementunique")).isInstanceOf(TransactionSystemException.class);
            assertThat(uncertainCache.isReady()).isFalse();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sensitive_words WHERE word='acknowledgementunique'", Integer.class)).isEqualTo(1);
            instance.reconcile();
            assertThat(uncertainCache.sanitize("acknowledgementunique")).isEqualTo("*".repeat(21));
        } finally {
            Long id = jdbc.queryForObject("SELECT id FROM sensitive_words WHERE word='acknowledgementunique'", Long.class);
            service.delete(id);
        }
    }

    @Test
    void aSecondInstanceAppliesCommittedRevisionsOnPolling() {
        var replicaCache = new MatcherCache();
        var replica = new SensitiveWordService(repository, replicaCache, transactionManager);
        replica.run(null);
        var created = service.create("replicaunique");
        try {
            assertThat(replicaCache.sanitize("replicaunique")).isEqualTo("replicaunique");
            replica.reconcile();
            assertThat(replicaCache.sanitize("replicaunique")).isEqualTo("*************");
        } finally {
            service.delete(created.id());
        }
        replica.reconcile();
        assertThat(replicaCache.sanitize("replicaunique")).isEqualTo("replicaunique");
    }

    @Test
    @Timeout(40)
    void sameNormalizedWordAcrossIndependentInstancesCommitsOnlyOnce() throws Exception {
        var other = new SensitiveWordService(repository, new MatcherCache(), transactionManager);
        other.run(null);
        var executor = Executors.newFixedThreadPool(2);
        try (var cleanup = concurrentCleanup(executor, List.of("raceunique"))) {
            var results = executor.invokeAll(List.<Callable<Boolean>>of(
                    () -> createOrConflict(service, "raceunique"),
                    () -> createOrConflict(other, "RACEUNIQUE")), 25, TimeUnit.SECONDS);
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sensitive_words WHERE normalized_word='raceunique'", Integer.class)).isEqualTo(1);
        }
    }

    private static boolean createOrConflict(SensitiveWordService instance, String word) {
        try {
            instance.create(word);
            return true;
        } catch (DuplicateWordException exception) {
            return false;
        }
    }

    @Test
    @Timeout(25)
    void blockedConfigurationWriteTimesOutAndRecovers() throws Exception {
        String url = "jdbc:sqlserver://" + SQL.getHost() + ":" + SQL.getMappedPort(1433)
                + ";databaseName=sensitive_words_test;encrypt=true;trustServerCertificate=true";
        try (var connection = java.sql.DriverManager.getConnection(url, SQL.getUsername(), SQL.getPassword())) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE vocabulary_configuration SET revision=revision WHERE id=1");
                long started = System.nanoTime();
                assertThatThrownBy(() -> service.create("locktimeoutunique")).isInstanceOf(org.springframework.dao.DataAccessException.class);
                assertThat(java.time.Duration.ofNanos(System.nanoTime() - started)).isLessThan(java.time.Duration.ofSeconds(15));
                assertThat(cache.isReady()).isFalse();
            } finally {
                connection.rollback();
            }
        }
        service.reconcile();
        assertThat(cache.isReady()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sensitive_words WHERE word='locktimeoutunique'", Integer.class)).isZero();
    }

    @Test
    void unicodeMigrationUpgradesExistingTermsAndRefusesCollisionsWithoutDataLoss() throws Exception {
        for (boolean collision : List.of(false, true)) {
            String database = collision ? "unicode_collision_test" : "unicode_upgrade_test";
            jdbc.execute("CREATE DATABASE " + database);
            try {
                String url = "jdbc:sqlserver://" + SQL.getHost() + ":" + SQL.getMappedPort(1433)
                        + ";databaseName=" + database + ";encrypt=true;trustServerCertificate=true";
                org.flywaydb.core.Flyway.configure().dataSource(url, SQL.getUsername(), SQL.getPassword()).target("2").load().migrate();
                var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(url, SQL.getUsername(), SQL.getPassword());
                var migrationJdbc = new JdbcTemplate(source);
                if (collision) {
                    migrationJdbc.update("INSERT INTO sensitive_words(word,normalized_word) VALUES(?,?),(?,?)", "σ", "σ", "ς", "ς");
                    assertThatThrownBy(() -> org.flywaydb.core.Flyway.configure().dataSource(source).load().migrate())
                            .isInstanceOf(org.flywaydb.core.api.FlywayException.class);
                    assertThat(migrationJdbc.queryForObject("SELECT COUNT(*) FROM sensitive_words", Integer.class)).isEqualTo(230);
                    assertThat(migrationJdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class)).isEqualTo(2);
                } else {
                    migrationJdbc.update("INSERT INTO sensitive_words(word,normalized_word) VALUES(?,?),(?,?)",
                            "İSTANBUL", "i\u0307stanbul", "\u00a0padunique\u00a0", "\u00a0padunique\u00a0");
                    org.flywaydb.core.Flyway.configure().dataSource(source).load().migrate();
                    assertThat(migrationJdbc.queryForObject("SELECT normalized_word FROM sensitive_words WHERE word=?", String.class, "İSTANBUL")).isEqualTo("istanbul");
                    assertThat(migrationJdbc.queryForObject("SELECT word FROM sensitive_words WHERE normalized_word='padunique'", String.class)).isEqualTo("padunique");
                }
            } finally {
                jdbc.execute("DROP DATABASE " + database);
            }
        }
    }

    @Test
    @Timeout(40)
    void simultaneousCreatesDoNotLoseCommittedMatcherEntries() throws Exception {
        List<String> terms = List.of("alphaunique", "betaunique", "gammaunique", "deltaunique");
        var executor = Executors.newFixedThreadPool(4);
        try (var cleanup = concurrentCleanup(executor, terms)) {
            var tasks = terms.stream().<Callable<Long>>map(term -> () -> service.create(term).id()).toList();
            var results = executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
            for (String term : terms) {
                assertThat(cache.sanitize(term)).isEqualTo("*".repeat(term.length()));
            }
            for (var result : results) {
                assertThat(result.get()).isPositive();
            }
        }
    }

    private AutoCloseable concurrentCleanup(java.util.concurrent.ExecutorService executor, List<String> terms) {
        return () -> {
            // Try-with-resources preserves the original test failure and suppresses cleanup failures.
            var failures = new java.util.ArrayList<Exception>();
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) failures.add(new IllegalStateException("Executor did not terminate"));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                failures.add(exception);
            }
            for (String term : terms) {
                try {
                    for (Long id : jdbc.queryForList("SELECT id FROM sensitive_words WHERE normalized_word=?",
                            Long.class, za.co.flash.sensitivewords.domain.TermNormalizer.normalize(term))) {
                        try {
                            service.delete(id);
                        } catch (Exception exception) {
                            failures.add(exception);
                        }
                    }
                } catch (Exception exception) {
                    failures.add(exception);
                }
            }
            if (!failures.isEmpty()) {
                var failure = new IllegalStateException("Concurrent test cleanup failed", failures.getFirst());
                failures.stream().skip(1).forEach(failure::addSuppressed);
                throw failure;
            }
        };
    }

    @Test
    void swaggerAndHealthAreAvailableAndDescribeEveryRoute() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        var spec = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var document = json.readTree(spec);
        assertThat(document.at("/components/schemas/ApiProblem/properties/status/type").asText()).isEqualTo("integer");
        assertThat(document.at("/components/schemas/SanitizeRequest/properties/text/maxLength").asInt()).isEqualTo(10000);
        var paths = document.get("paths");
        assertThat(paths.get("/api/v1/sanitize").get("post").get("responses").has("200")).isTrue();
        assertThat(paths.get(WORDS).get("post").get("responses").has("409")).isTrue();
        assertThat(paths.get(WORDS).get("post").get("responses").get("201").get("headers").has("Location")).isTrue();
        assertThat(paths.get("/api/v1/sanitize").get("post").get("responses").has("413")).isTrue();
        assertThat(paths.get(WORDS).get("post").get("responses").get("409")
                .get("content").get("application/problem+json").get("example").get("status").asInt()).isEqualTo(409);
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
