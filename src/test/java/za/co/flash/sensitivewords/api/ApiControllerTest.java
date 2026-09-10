package za.co.flash.sensitivewords.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.flash.sensitivewords.application.*;
import za.co.flash.sensitivewords.config.SanitizationProperties;
import za.co.flash.sensitivewords.dto.SensitiveWordResponse;
import za.co.flash.sensitivewords.exception.*;
import za.co.flash.sensitivewords.matcher.*;

@WebMvcTest(controllers = {SanitizationController.class, SensitiveWordController.class},
        properties = "sanitization.max-message-length=50")
@Import({SanitizationService.class, MatcherCache.class})
@EnableConfigurationProperties(SanitizationProperties.class)
class ApiControllerTest {
    private static final String WORDS = "/api/v1/internal/sensitive-words";
    @Autowired private MockMvc mvc;
    @Autowired private MatcherCache cache;
    @MockitoBean private SensitiveWordService words;
    private final SensitiveWordResponse response = new SensitiveWordResponse(7L, "SECRET", Instant.EPOCH, Instant.EPOCH);

    @BeforeEach
    void initializeMatcher() {
        cache.publish(SensitiveWordMatcher.compile(List.of("CREATE", "SELECT", "SELECT * FROM")));
    }

    @Test
    void sanitizationRouteReturnsDocumentedJson() throws Exception {
        mvc.perform(post("/api/v1/sanitize").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"SELECT * FROM sensitiveWords\"}"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.original").value("SELECT * FROM sensitiveWords"))
                .andExpect(jsonPath("$.sanitized").value("****** * FROM sensitiveWords"));
        verifyNoInteractions(words);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"text\":null}", "{\"text\":\" \"}", "{", "null", "[]", "{\"text\":{}}", "{\"text\":\"x\",\"unexpected\":true}"})
    void invalidSanitizeBodyReturnsSafeProblemJson(String body) throws Exception {
        mvc.perform(post("/api/v1/sanitize").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400)).andExpect(jsonPath("$.detail").exists())
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void messageLengthLimitIsConfigurable() throws Exception {
        mvc.perform(post("/api/v1/sanitize").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + "x".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("50")));
    }

    @Test
    void createReturnsLocationAndDto() throws Exception {
        when(words.create("SECRET")).thenReturn(response);
        mvc.perform(post(WORDS).contentType(MediaType.APPLICATION_JSON).content("{\"word\":\"SECRET\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "http://localhost" + WORDS + "/7"))
                .andExpect(jsonPath("$.id").value(7)).andExpect(jsonPath("$.word").value("SECRET"))
                .andExpect(jsonPath("$.normalizedWord").doesNotExist());
    }

    @Test
    void getAndListExposeDtoAndStablePaginationContract() throws Exception {
        when(words.get(7)).thenReturn(response);
        when(words.list(0, 20)).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));
        mvc.perform(get(WORDS + "/7")).andExpect(status().isOk()).andExpect(jsonPath("$.word").value("SECRET"));
        mvc.perform(get(WORDS)).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.page.totalElements").value(1)).andExpect(jsonPath("$.page.size").value(20));
    }

    @Test
    void updateAndDeleteHaveCorrectHttpSemantics() throws Exception {
        when(words.update(7, "SECRET")).thenReturn(response);
        mvc.perform(put(WORDS + "/7").contentType(MediaType.APPLICATION_JSON).content("{\"word\":\"SECRET\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.word").value("SECRET"));
        mvc.perform(delete(WORDS + "/7")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(words).delete(7);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"word\":null}", "{\"word\":\" \"}", "{"})
    void validatesCrudBody(String body) throws Exception {
        mvc.perform(post(WORDS).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(put(WORDS + "/7").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(words);
    }

    @Test
    void rejectsOversizedTerm() throws Exception {
        mvc.perform(post(WORDS).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"word\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(words);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/0", "/-1", "/abc", "/99999999999999999999999", "?page=-1", "?size=0", "?size=101", "?page=abc"})
    void invalidIdsAndPaginationAreBadRequests(String suffix) throws Exception {
        mvc.perform(get(WORDS + suffix)).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(words);
    }

    @Test
    void missingResourcesAndDuplicatesHaveExpectedStatuses() throws Exception {
        when(words.get(99)).thenThrow(new WordNotFoundException(99));
        when(words.update(99, "SECRET")).thenThrow(new WordNotFoundException(99));
        doThrow(new WordNotFoundException(99)).when(words).delete(99);
        when(words.create("SECRET")).thenThrow(new DuplicateWordException());
        mvc.perform(get(WORDS + "/99")).andExpect(status().isNotFound());
        mvc.perform(put(WORDS + "/99").contentType(MediaType.APPLICATION_JSON).content("{\"word\":\"SECRET\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(WORDS + "/99")).andExpect(status().isNotFound());
        mvc.perform(post(WORDS).contentType(MediaType.APPLICATION_JSON).content("{\"word\":\"SECRET\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @ParameterizedTest
    @ValueSource(ints = {2601, 2627})
    void databaseUniqueRacesAreConflictsWithoutSqlDetails(int code) throws Exception {
        when(words.create(anyString())).thenThrow(new DataIntegrityViolationException("private SQL detail",
                new SQLException("private SQL detail", "23000", code)));
        mvc.perform(post(WORDS).contentType(MediaType.APPLICATION_JSON).content("{\"word\":\"SECRET\"}"))
                .andExpect(status().isConflict()).andExpect(content().string(not(containsString("private SQL"))));
    }

    @Test
    void otherDatabaseFailuresAreNotMisreportedAsDuplicates() throws Exception {
        when(words.get(7)).thenThrow(new DataIntegrityViolationException("private SQL detail"));
        mvc.perform(get(WORDS + "/7")).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("private SQL"))));
    }

    @Test
    void unsupportedMediaTypeAndMethodAreHandled() throws Exception {
        mvc.perform(post("/api/v1/sanitize").contentType(MediaType.TEXT_PLAIN).content("CREATE"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(get("/api/v1/sanitize")).andExpect(status().isMethodNotAllowed());
    }
}
