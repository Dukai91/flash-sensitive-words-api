package za.co.flash.sensitivewords.matcher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import za.co.flash.sensitivewords.config.VocabularyProperties;
import za.co.flash.sensitivewords.exception.MatcherUnavailableException;

@Component
public class MatcherCache {
    private final AtomicReference<Snapshot> current = new AtomicReference<>();
    private final Clock clock;
    private final Duration maxAge;

    @Autowired
    public MatcherCache(VocabularyProperties properties) {
        this(Clock.systemUTC(), Duration.ofSeconds(properties.maxStaleSeconds()));
    }

    public MatcherCache() {
        this(Clock.systemUTC(), Duration.ofMinutes(5));
    }

    MatcherCache(Clock clock, Duration maxAge) {
        this.clock = clock;
        this.maxAge = maxAge;
    }

    public void publish(SensitiveWordMatcher matcher) {
        publish(matcher, 0);
    }

    public void publish(SensitiveWordMatcher matcher, long revision) {
        current.set(new Snapshot(Objects.requireNonNull(matcher), revision, clock.instant()));
    }

    public void confirm(long revision) {
        current.updateAndGet(snapshot -> snapshot != null && snapshot.revision() == revision
                ? new Snapshot(snapshot.matcher(), revision, clock.instant()) : snapshot);
    }

    public long revision() {
        var snapshot = current.get();
        return snapshot == null ? -1 : snapshot.revision();
    }

    public void invalidate() {
        current.set(null);
    }

    public boolean isReady() {
        return usable(current.get());
    }

    private boolean usable(Snapshot snapshot) {
        return snapshot != null && clock.instant().isBefore(snapshot.confirmedAt().plus(maxAge));
    }

    public String sanitize(String text) {
        Snapshot snapshot = current.get();
        if (!usable(snapshot)) {
            throw new MatcherUnavailableException();
        }
        return snapshot.matcher().sanitize(text);
    }

    private record Snapshot(SensitiveWordMatcher matcher, long revision, Instant confirmedAt) {}
}
