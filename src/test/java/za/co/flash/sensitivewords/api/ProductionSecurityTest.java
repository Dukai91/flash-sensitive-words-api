package za.co.flash.sensitivewords.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.flash.sensitivewords.application.SanitizationService;
import za.co.flash.sensitivewords.application.SensitiveWordService;
import za.co.flash.sensitivewords.config.*;

@WebMvcTest(controllers = {SanitizationController.class, SensitiveWordController.class},
        properties = {"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
                "spring.security.oauth2.resourceserver.jwt.audiences=flash-sensitive-words"})
@ActiveProfiles("production")
@Import({SecurityConfiguration.class, JsonConfiguration.class})
@EnableConfigurationProperties(SanitizationProperties.class)
class ProductionSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean SensitiveWordService words;
    @MockitoBean SanitizationService sanitizer;
    @MockitoBean JwtDecoder decoder;

    @Test
    void missingCredentialsAreUnauthorizedAndNeverRedirectToLogin() throws Exception {
        mvc.perform(get("/api/v1/internal/sensitive-words/1"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void invalidTokensReturnSafeProblemDetails() throws Exception {
        org.mockito.Mockito.when(decoder.decode("invalid")).thenThrow(new org.springframework.security.oauth2.jwt.BadJwtException("private decoder failure"));
        mvc.perform(get("/api/v1/internal/sensitive-words/1").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("A valid service access token is required"));
    }

    @Test
    void readScopeCannotMutateVocabulary() throws Exception {
        mvc.perform(delete("/api/v1/internal/sensitive-words/1").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_words:read"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/v1/internal/sensitive-words/1").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_words:read"))))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/internal/sensitive-words/1").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_words:write"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void businessScopeDoesNotGrantAdministrationAndSwaggerIsDenied() throws Exception {
        mvc.perform(get("/api/v1/internal/sensitive-words/1").with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_sanitize"))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/sanitize").contentType("application/json").content("{\"text\":\"CREATE\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_sanitize"))))
                .andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/index.html").with(jwt())).andExpect(status().isForbidden());
    }
}
