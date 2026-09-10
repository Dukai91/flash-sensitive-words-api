package za.co.flash.sensitivewords.domain;

import za.co.flash.sensitivewords.exception.InvalidInputException;

public final class TermNormalizer {
    public static final int MAX_LENGTH = 200;

    private TermNormalizer() {}

    public static String clean(String word) {
        if (word == null) {
            throw new InvalidInputException("word must not be null");
        }
        int start = 0;
        int end = word.length();
        while (start < end && isSpace(word.codePointAt(start))) {
            start += Character.charCount(word.codePointAt(start));
        }
        while (end > start && isSpace(word.codePointBefore(end))) {
            end -= Character.charCount(word.codePointBefore(end));
        }
        String cleaned = word.substring(start, end);
        if (cleaned.isBlank() || cleaned.length() > MAX_LENGTH
                || cleaned.codePoints().anyMatch(Character::isISOControl)
                || cleaned.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new InvalidInputException("word must contain 1 to 200 characters and no control characters");
        }
        return cleaned;
    }

    public static String normalize(String word) {
        return foldCase(clean(word));
    }

    public static String foldCase(String value) {
        StringBuilder folded = null;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            int canonical = Character.toLowerCase(Character.toUpperCase(codePoint));
            if (canonical != codePoint && folded == null) {
                folded = new StringBuilder(value.length()).append(value, 0, offset);
            }
            if (folded != null) folded.appendCodePoint(canonical);
            offset += Character.charCount(codePoint);
        }
        return folded == null ? value : folded.toString();
    }

    public static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    public static boolean isBlank(String value) {
        return value == null || value.codePoints().allMatch(TermNormalizer::isSpace);
    }
}
