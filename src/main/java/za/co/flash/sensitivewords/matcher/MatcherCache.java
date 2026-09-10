package za.co.flash.sensitivewords.matcher;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;
import za.co.flash.sensitivewords.exception.MatcherUnavailableException;

@Component
public class MatcherCache {
    private final AtomicReference<SensitiveWordMatcher> current = new AtomicReference<>();

    public void publish(SensitiveWordMatcher matcher) {
        current.set(java.util.Objects.requireNonNull(matcher));
    }

    public String sanitize(String text) {
        SensitiveWordMatcher snapshot = current.get();
        if (snapshot == null) {
            throw new MatcherUnavailableException();
        }
        return snapshot.sanitize(text);
    }
}
