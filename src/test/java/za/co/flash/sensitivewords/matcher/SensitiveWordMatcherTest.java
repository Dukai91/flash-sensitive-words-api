package za.co.flash.sensitivewords.matcher;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SensitiveWordMatcherTest {
    @ParameterizedTest
    @ValueSource(strings = {"İSTANBUL", "ı", "ς", "σ", "Σ", "K", "ſ", "ß", "ẞ", "𐐀", "é", "é"})
    void everyAcceptedUnicodeTermMatchesItself(String term) {
        assertThat(SensitiveWordMatcher.compile(List.of(term)).sanitize(term))
                .isEqualTo("*".repeat(term.codePointCount(0, term.length())));
    }

    @Test
    void exposesTheGeneralPhrasePrecedenceAndUnicodeEquivalenceRules() {
        assertThat(SensitiveWordMatcher.compile(List.of("foo", "foo bar")).sanitize("foo bar")).isEqualTo("*** bar");
        assertThat(SensitiveWordMatcher.compile(List.of("σ")).sanitize("σ ς Σ")).isEqualTo("* * *");
        assertThat(SensitiveWordMatcher.compile(List.of("\u00a0CREATE\u202f")).sanitize("CREATE")).isEqualTo("******");
    }

    private final SensitiveWordMatcher matcher = SensitiveWordMatcher.compile(
            List.of("CREATE", "TABLE", "DROP", "ORDER", "SELECT", "SELECT * FROM"));

    @ParameterizedTest
    @ValueSource(strings = {"CREATE", "create", "Create", "CrEaTe"})
    void masksEveryCase(String input) {
        assertThat(matcher.sanitize(input)).isEqualTo("******");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "CREATE TABLE|****** *****",
            "CREATE x CREATE|****** x ******",
            "CREATE, DROP!|******, ****!",
            "Please CREATE a TABLE|Please ****** a *****",
            "preorder ORDER reordered|preorder ***** reordered",
            "CREATE_table CREATE2 éCREATE CREATEé CREATÉ|CREATE_table CREATE2 éCREATE CREATEé CREATÉ",
            "SELECT * FROM sensitiveWords|****** * FROM sensitiveWords",
            "Hello, world!|Hello, world!",
            "(CREATE) [DROP]|(******) [****]"})
    void respectsBoundariesAndPreservesUnrelatedContent(String input, String expected) {
        assertThat(matcher.sanitize(input)).isEqualTo(expected);
    }

    @Test
    void preservesExactWhitespace() {
        assertThat(matcher.sanitize("  CREATE\t\nDROP  ")).isEqualTo("  ******\t\n****  ");
    }

    @Test
    void matchesLiteralPhrasesWhenNoShorterAlternativeMatches() {
        var phrases = SensitiveWordMatcher.compile(List.of("SELECT * FROM", "a+b", "top secret"));
        assertThat(phrases.sanitize("select * from x; a+b; TOP SECRET")).isEqualTo("****** * **** x; ***; *** ******");
        assertThat(phrases.sanitize("top  secret")).isEqualTo("top  secret");
    }

    @Test
    void precedenceDoesNotDependOnDatabaseOrder() {
        var other = SensitiveWordMatcher.compile(List.of("SELECT * FROM", "select", " SELECT "));
        assertThat(other.sanitize("SELECT * FROM sensitiveWords")).isEqualTo("****** * FROM sensitiveWords");
    }

    @Test
    void longerAlternativeCanMatchWhenShorterOneHasNoBoundary() {
        assertThat(SensitiveWordMatcher.compile(List.of("ORDER", "ORDERLY")).sanitize("orderly"))
                .isEqualTo("*******");
    }

    @Test
    void supplementaryCodePointsProduceOneStarEach() {
        assertThat(SensitiveWordMatcher.compile(List.of("🔒")).sanitize("🔒 hello")).isEqualTo("* hello");
    }

    @Test
    void emptyVocabularyAndEmptyMessageAreSafe() {
        assertThat(SensitiveWordMatcher.compile(List.of()).sanitize("CREATE")).isEqualTo("CREATE");
        assertThat(matcher.sanitize("")).isEmpty();
    }
}
