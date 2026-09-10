package za.co.flash.sensitivewords;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import za.co.flash.sensitivewords.domain.TermNormalizer;
import za.co.flash.sensitivewords.matcher.SensitiveWordMatcher;

class SeedSourceTest {
    static List<String> suppliedWords() throws Exception {
        return new ObjectMapper().readValue(Path.of("docs/assessment/sql_sensitive_list.txt").toFile(), new TypeReference<>() {});
    }

    @Test
    void migrationContainsExactlyTheSuppliedDatasetInSourceOrder() throws Exception {
        var supplied = suppliedWords();
        var migration = Files.readString(Path.of("src/main/resources/db/migration/V2__seed_sensitive_words.sql"));
        var rows = Pattern.compile("\\(N'((?:[^']|'')*)', N'((?:[^']|'')*)'\\)").matcher(migration);
        List<String> seeded = new ArrayList<>();
        while (rows.find()) {
            String word = rows.group(1).replace("''", "'");
            seeded.add(word);
            assertThat(rows.group(2).replace("''", "'")).isEqualTo(TermNormalizer.normalize(word));
        }
        assertThat(seeded).hasSize(228).containsExactlyElementsOf(supplied);
        assertThat(seeded).doesNotHaveDuplicates().contains("SELECT * FROM");
    }

    @Test
    void flashPdfRegressionUsesTheCompleteSuppliedList() throws Exception {
        assertThat(SensitiveWordMatcher.compile(suppliedWords()).sanitize("SELECT * FROM sensitiveWords"))
                .isEqualTo("****** * FROM sensitiveWords");
    }
}
