package za.co.flash.sensitivewords.matcher;

import java.util.Collection;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import za.co.flash.sensitivewords.domain.TermNormalizer;

/** Immutable and safe to share; each invocation creates its own regex Matcher. */
public final class SensitiveWordMatcher {
    private static final String TOKEN_CHARACTER = "[\\p{L}\\p{N}\\p{M}\\p{Pc}]";
    private final Pattern pattern;

    private SensitiveWordMatcher(Pattern pattern) {
        this.pattern = pattern;
    }

    public static SensitiveWordMatcher compile(Collection<String> words) {
        // Shorter alternatives at the same position preserve Flash's explicit SELECT example.
        String alternatives = words.stream().map(TermNormalizer::normalize).distinct()
                .sorted(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))
                .map(Pattern::quote).collect(Collectors.joining("|"));
        return new SensitiveWordMatcher(alternatives.isEmpty() ? null : Pattern.compile(
                "(?<!" + TOKEN_CHARACTER + ")(?:" + alternatives + ")(?!" + TOKEN_CHARACTER + ")"));
    }

    public String sanitize(String text) {
        if (pattern == null) {
            return text;
        }
        // Simple folding is shared with duplicate identity. On Java 21 it preserves
        // UTF-16 offsets; an exhaustive runtime invariant test protects that contract.
        var matcher = pattern.matcher(TermNormalizer.foldCase(text));
        StringBuilder result = null;
        int last = 0;
        while (matcher.find()) {
            if (result == null) {
                result = new StringBuilder(text.length());
            }
            result.append(text, last, matcher.start());
            for (int offset = matcher.start(); offset < matcher.end();) {
                int codePoint = text.codePointAt(offset);
                if (TermNormalizer.isSpace(codePoint)) {
                    result.appendCodePoint(codePoint);
                } else {
                    result.append('*');
                }
                offset += Character.charCount(codePoint);
            }
            last = matcher.end();
        }
        return result == null ? text : result.append(text, last, text.length()).toString();
    }
}
