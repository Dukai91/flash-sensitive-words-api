package za.co.flash.sensitivewords.matcher;

import static org.assertj.core.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import za.co.flash.sensitivewords.exception.MatcherUnavailableException;

class MatcherCacheTest {
    @Test
    void freshnessExpiryConfirmationAndInvalidationControlReadiness() {
        var now = new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.EPOCH);
        var clock = new java.time.Clock() {
            @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
            @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public java.time.Instant instant() { return now.get(); }
        };
        var cache = new MatcherCache(clock, java.time.Duration.ofSeconds(5));
        var health = new za.co.flash.sensitivewords.config.MatcherHealthIndicator(cache);
        assertThat(health.health().getStatus()).isEqualTo(org.springframework.boot.actuate.health.Status.DOWN);
        cache.publish(SensitiveWordMatcher.compile(List.of("CREATE")), 7);
        assertThat(health.health().getStatus()).isEqualTo(org.springframework.boot.actuate.health.Status.UP);
        now.set(java.time.Instant.EPOCH.plusSeconds(5));
        assertThatThrownBy(() -> cache.sanitize("CREATE")).isInstanceOf(MatcherUnavailableException.class);
        cache.confirm(6);
        assertThat(cache.isReady()).isFalse();
        cache.confirm(7);
        assertThat(cache.sanitize("CREATE")).isEqualTo("******");
        cache.invalidate();
        cache.confirm(7);
        assertThat(cache.isReady()).isFalse();
    }

    @Test
    void refusesRequestsUntilInitialized() {
        assertThatThrownBy(() -> new MatcherCache().sanitize("CREATE"))
                .isInstanceOf(MatcherUnavailableException.class);
    }

    @Test
    @Timeout(15)
    void concurrentReadersSeeACompleteOldOrNewSnapshot() throws Exception {
        var cache = new MatcherCache();
        var oldMatcher = SensitiveWordMatcher.compile(List.of("CREATE"));
        var newMatcher = SensitiveWordMatcher.compile(List.of("DROP"));
        cache.publish(oldMatcher);
        var start = new CountDownLatch(1);
        var readOld = new CountDownLatch(4);
        var publishedNew = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(5)) {
            List<Future<?>> futures = new ArrayList<>();
            futures.add(executor.submit(() -> {
                await(start);
                await(readOld);
                for (int i = 0; i < 2000; i++) {
                    cache.publish(i % 2 == 0 ? newMatcher : oldMatcher);
                }
                cache.publish(newMatcher);
                publishedNew.countDown();
            }));
            for (int reader = 0; reader < 4; reader++) {
                futures.add(executor.submit(() -> {
                    await(start);
                    assertThat(cache.sanitize("CREATE DROP")).isEqualTo("****** DROP");
                    readOld.countDown();
                    for (int i = 0; i < 2000; i++) {
                        assertThat(cache.sanitize("CREATE DROP CREATE DROP"))
                                .isIn("****** DROP ****** DROP", "CREATE **** CREATE ****");
                    }
                    await(publishedNew);
                    assertThat(cache.sanitize("CREATE DROP")).isEqualTo("CREATE ****");
                }));
            }
            start.countDown();
            for (var future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Concurrent test did not start");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
