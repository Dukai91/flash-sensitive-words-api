package za.co.flash.sensitivewords.application;

import org.springframework.stereotype.Service;
import za.co.flash.sensitivewords.domain.TermNormalizer;
import za.co.flash.sensitivewords.config.SanitizationProperties;
import za.co.flash.sensitivewords.dto.SanitizeResponse;
import za.co.flash.sensitivewords.exception.InvalidInputException;
import za.co.flash.sensitivewords.matcher.MatcherCache;

@Service
public class SanitizationService {
    private final MatcherCache cache;
    private final SanitizationProperties properties;

    public SanitizationService(MatcherCache cache, SanitizationProperties properties) {
        this.cache = cache;
        this.properties = properties;
    }

    public SanitizeResponse sanitize(String text) {
        if (TermNormalizer.isBlank(text)) {
            throw new InvalidInputException("text must not be blank");
        }
        if (text.length() > properties.maxMessageLength()) {
            throw new InvalidInputException("text must contain at most " + properties.maxMessageLength() + " UTF-16 units");
        }
        return new SanitizeResponse(text, cache.sanitize(text));
    }
}
