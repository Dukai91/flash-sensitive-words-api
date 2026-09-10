package za.co.flash.sensitivewords.matcher;

import java.util.Collection;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import za.co.flash.sensitivewords.application.TermNormalizer;

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
                "(?<!" + TOKEN_CHARACTER + ")(?:" + alternatives + ")(?!" + TOKEN_CHARACTER + ")",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }

    public String sanitize(String text) {
        if (pattern == null) {
            return text;
        }
        var matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder(text.length());
        int last = 0;
        while (matcher.find()) {
            result.append(text, last, matcher.start());
            text.substring(matcher.start(), matcher.end()).codePoints().forEach(codePoint -> {
                if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                    result.appendCodePoint(codePoint);
                } else {
                    result.append('*');
                }
            });
            last = matcher.end();
        }
        return last == 0 ? text : result.append(text, last, text.length()).toString();
    }
}
