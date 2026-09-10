package db.migration;

import java.util.ArrayList;
import java.util.HashSet;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Rekeys pre-existing user terms without editing the applied seed migration.
 * A collision aborts the transaction; operators must resolve it through the old API first.
 * This migration's normalization rules must remain frozen after release.
 */
public class V3__normalize_unicode_and_version_vocabulary extends BaseJavaMigration {
    @Override
    public Integer getChecksum() {
        return 3001;
    }

    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        record Row(long id, String word, String key) {}
        var rows = new ArrayList<Row>();
        var keys = new HashSet<String>();
        try (var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT id, word FROM dbo.sensitive_words")) {
            while (result.next()) {
                // Freeze V3's algorithm here rather than depending on mutable application code.
                String raw = result.getNString(2);
                int start = 0;
                int end = raw.length();
                while (start < end && space(raw.codePointAt(start))) start += Character.charCount(raw.codePointAt(start));
                while (end > start && space(raw.codePointBefore(end))) end -= Character.charCount(raw.codePointBefore(end));
                String word = raw.substring(start, end);
                var key = new StringBuilder();
                word.codePoints().map(c -> Character.toLowerCase(Character.toUpperCase(c))).forEach(key::appendCodePoint);
                if (word.isEmpty() || word.length() > 200 || word.codePoints().anyMatch(Character::isISOControl)) {
                    throw new IllegalStateException("Invalid stored vocabulary; resolve before upgrading");
                }
                if (!keys.add(key.toString())) {
                    throw new IllegalStateException("Unicode normalization collision; resolve duplicate terms before upgrading");
                }
                rows.add(new Row(result.getLong(1), word, key.toString()));
            }
        }
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE dbo.sensitive_words DROP CONSTRAINT uq_sensitive_words_normalized");
        }
        try (var update = connection.prepareStatement("UPDATE dbo.sensitive_words SET word=?,normalized_word=? WHERE id=?")) {
            for (var row : rows) {
                update.setNString(1, row.word());
                update.setNString(2, row.key());
                update.setLong(3, row.id());
                update.addBatch();
            }
            update.executeBatch();
        }
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE dbo.sensitive_words ADD CONSTRAINT uq_sensitive_words_normalized UNIQUE(normalized_word)");
            statement.execute("CREATE TABLE dbo.vocabulary_configuration (id INT NOT NULL PRIMARY KEY CHECK(id=1), revision BIGINT NOT NULL CHECK(revision>=0))");
            statement.execute("INSERT INTO dbo.vocabulary_configuration(id,revision) VALUES(1,0)");
        }
    }

    private static boolean space(int value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }
}
