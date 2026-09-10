package za.co.flash.sensitivewords.application;

import static org.assertj.core.api.Assertions.*;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import za.co.flash.sensitivewords.exception.InvalidInputException;

class TermNormalizerTest {
    @ParameterizedTest
    @ValueSource(strings = {" CREATE ", "create", "Create"})
    void normalizesEquivalentValues(String value) {
        assertThat(TermNormalizer.normalize(value)).isEqualTo("create");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\u00a0", "foo\nbar", "foo\u0000bar"})
    void rejectsInvalidWords(String value) {
        assertThatThrownBy(() -> TermNormalizer.clean(value)).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void enforcesMaximumLength() {
        assertThat(TermNormalizer.clean("x".repeat(200))).hasSize(200);
        assertThatThrownBy(() -> TermNormalizer.clean("x".repeat(201))).isInstanceOf(InvalidInputException.class);
    }

    @Test
    void normalizationIsIndependentOfDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(TermNormalizer.normalize("INSERT")).isEqualTo("insert");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
