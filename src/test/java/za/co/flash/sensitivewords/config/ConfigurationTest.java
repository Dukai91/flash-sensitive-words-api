package za.co.flash.sensitivewords.config;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ConfigurationTest {
    @Test
    void productionRejectsDevelopmentDatabaseSettings() {
        var properties = new org.springframework.boot.autoconfigure.jdbc.DataSourceProperties();
        properties.setUrl("jdbc:sqlserver://db;encrypt=true;trustServerCertificate=true");
        properties.setUsername("sa");
        assertThatThrownBy(() -> new ProductionDatabaseGuard(properties).afterPropertiesSet()).isInstanceOf(IllegalStateException.class);
        properties.setUsername("flash_runtime");
        properties.setUrl("jdbc:sqlserver://db;encrypt=true;trustServerCertificate=false");
        assertThatCode(() -> new ProductionDatabaseGuard(properties).afterPropertiesSet()).doesNotThrowAnyException();
    }
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({SanitizationProperties.class, VocabularyProperties.class})
    static class Properties {}

    @Test
    void invalidLimitsFailBindingInsteadOfStartingWithUnsafeValues() {
        var runner = new ApplicationContextRunner().withUserConfiguration(Properties.class);
        runner.withPropertyValues("sanitization.max-message-length=0").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("vocabulary.max-stale-seconds=0").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("vocabulary.max-terms=1").run(context -> assertThat(context).hasFailed());
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(VocabularyProperties.class).maxStaleSeconds()).isEqualTo(300);
        });
    }

    @Test
    void unknownLengthBodyIsStillBoundedBeforeTheController() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/sanitize") {
            @Override public long getContentLengthLong() { return -1; }
        };
        request.setContent(new byte[2400]);
        var response = new MockHttpServletResponse();
        var filter = new RequestBodyLimitFilter(new SanitizationProperties(50), new com.fasterxml.jackson.databind.ObjectMapper());
        filter.doFilter(request, response, (req, res) -> fail("Oversized body reached controller"));
        assertThat(response.getStatus()).isEqualTo(413);
    }
}
