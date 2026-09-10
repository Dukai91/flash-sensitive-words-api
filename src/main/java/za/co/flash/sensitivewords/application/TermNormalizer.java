package za.co.flash.sensitivewords.application;

import java.util.Locale;
import za.co.flash.sensitivewords.exception.InvalidInputException;

public final class TermNormalizer {
    public static final int MAX_LENGTH = 200;

    private TermNormalizer() {}

    public static String clean(String word) {
        if (word == null) {
            throw new InvalidInputException("word must not be null");
        }
        String cleaned = word.strip();
        if (cleaned.isBlank() || cleaned.length() > MAX_LENGTH
                || cleaned.toLowerCase(Locale.ROOT).length() > MAX_LENGTH
                || cleaned.codePoints().anyMatch(Character::isISOControl)
                || cleaned.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
            throw new InvalidInputException("word must contain 1 to 200 characters and no control characters");
        }
        return cleaned;
    }

    public static String normalize(String word) {
        return clean(word).toLowerCase(Locale.ROOT);
    }
}
