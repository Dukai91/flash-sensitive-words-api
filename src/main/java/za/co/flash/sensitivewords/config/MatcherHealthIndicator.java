package za.co.flash.sensitivewords.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import za.co.flash.sensitivewords.matcher.MatcherCache;

@Component("matcher")
public class MatcherHealthIndicator implements HealthIndicator {
    private final MatcherCache cache;

    public MatcherHealthIndicator(MatcherCache cache) {
        this.cache = cache;
    }

    @Override
    public Health health() {
        return (cache.isReady() ? Health.up() : Health.down()).build();
    }
}
