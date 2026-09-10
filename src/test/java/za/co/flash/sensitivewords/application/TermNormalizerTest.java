package za.co.flash.sensitivewords.application;

import static org.assertj.core.api.Assertions.*;
import java.util.Locale;
import za.co.flash.sensitivewords.domain.TermNormalizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import za.co.flash.sensitivewords.exception.InvalidInputException;

class TermNormalizerTest {
    @Test
    void allRuntimeSimpleCaseMappingsPreserveUtf16OffsetsAndAreIdempotent() {
        for (int value = 0; value <= Character.MAX_CODE_POINT; value++) {
            int folded = Character.toLowerCase(Character.toUpperCase(value));
            assertThat(Character.charCount(folded)).as("UTF-16 width for U+%04X", value)
                    .isEqualTo(Character.charCount(value));
            assertThat(Character.toLowerCase(Character.toUpperCase(folded))).isEqualTo(folded);
        }
    }
    @Test
    void simpleUnicodeCaseFoldingAndWhitespaceAreConsistent() {
        assertThat(TermNormalizer.normalize("\u0130STANBUL")).isEqualTo("istanbul");
        assertThat(TermNormalizer.normalize("\u03c2")).isEqualTo(TermNormalizer.normalize("\u03c3"));
        assertThat(TermNormalizer.normalize("\u00a0CREATE\u202f")).isEqualTo("create");
        assertThat(TermNormalizer.isBlank("\u00a0\u202f")).isTrue();
    }

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
