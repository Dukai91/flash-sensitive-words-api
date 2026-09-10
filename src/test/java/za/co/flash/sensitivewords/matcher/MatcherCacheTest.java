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
        try (var executor = Executors.newFixedThreadPool(5)) {
            List<Future<?>> futures = new ArrayList<>();
            futures.add(executor.submit(() -> {
                await(start);
                for (int i = 0; i < 2000; i++) {
                    cache.publish(i % 2 == 0 ? newMatcher : oldMatcher);
                }
            }));
            for (int reader = 0; reader < 4; reader++) {
                futures.add(executor.submit(() -> {
                    await(start);
                    for (int i = 0; i < 2000; i++) {
                        assertThat(cache.sanitize("CREATE DROP CREATE DROP"))
                                .isIn("****** DROP ****** DROP", "CREATE **** CREATE ****");
                    }
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
