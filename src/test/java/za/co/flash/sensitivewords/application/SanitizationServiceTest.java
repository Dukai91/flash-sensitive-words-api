package za.co.flash.sensitivewords.application;

import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import za.co.flash.sensitivewords.config.SanitizationProperties;
import za.co.flash.sensitivewords.exception.InvalidInputException;
import za.co.flash.sensitivewords.matcher.MatcherCache;
import za.co.flash.sensitivewords.matcher.SensitiveWordMatcher;

class SanitizationServiceTest {
    private SanitizationService service;

    @BeforeEach
    void setUp() {
        var cache = new MatcherCache();
        cache.publish(SensitiveWordMatcher.compile(List.of("CREATE")));
        service = new SanitizationService(cache, new SanitizationProperties(20));
    }

    @Test
    void preservesOriginalAndSanitizesWithoutADatabaseDependency() {
        var response = service.sanitize(" CREATE ");
        assertThat(response.original()).isEqualTo(" CREATE ");
        assertThat(response.sanitized()).isEqualTo(" ****** ");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankMessages(String text) {
        assertThatThrownBy(() -> service.sanitize(text)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void appliesConfiguredLimitInclusively() {
        assertThat(service.sanitize("x".repeat(20)).sanitized()).hasSize(20);
        assertThatThrownBy(() -> service.sanitize("x".repeat(21))).isInstanceOf(InvalidInputException.class);
    }
}
