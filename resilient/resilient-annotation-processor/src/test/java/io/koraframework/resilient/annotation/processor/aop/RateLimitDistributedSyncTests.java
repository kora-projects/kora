package io.koraframework.resilient.annotation.processor.aop;

import io.koraframework.resilient.ratelimiter.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitDistributedSyncTests extends ResilientAopTestSupport {

    @Test
    void distributedRateLimitFirstCallSucceeds() {
        var service = compileDistributedRateLimitTarget("""
            @RateLimited(TestRateLimiter.class)
            public String call() {
                return "OK";
            }
            """);

        assertEquals("OK", invoke(service, "call"));
    }

    @Test
    void distributedRateLimitSecondCallExceedsLimit() {
        var service = compileDistributedRateLimitTarget("""
            @RateLimited(TestRateLimiter.class)
            public String call() {
                return "OK";
            }
            """);

        invoke(service, "call");

        assertThrows(RateLimitExceededException.class, () -> invoke(service, "call"));
    }

    private Object compileDistributedRateLimitTarget(String method) {
        return compileApp("""
            custom1 {
              limitForPeriod = 1
              limitRefreshPeriod = 10s
              keyPrefix = "test"
              algorithm = "FIXED_WINDOW"
            }
            """, """
            @io.koraframework.resilient.distributed.ratelimiter.annotation.RateLimiterDistributedSpec("custom1")
            public interface TestRateLimiter extends io.koraframework.resilient.ratelimiter.RateLimiter {}
            """, """
            @Component
            @Root
            public class TestTarget {
                %s
            }

            @Component
            class FakeDistributedRateLimiterClient implements io.koraframework.resilient.distributed.ratelimiter.DistributedRateLimiterClient {
                private final java.util.concurrent.ConcurrentHashMap<String, Long> store = new java.util.concurrent.ConcurrentHashMap<>();

                public FakeDistributedRateLimiterClient() {}

                public long incrementAndExpire(String key, long ttlMillis) {
                    return store.merge(key, 1L, Long::sum);
                }

                public long addAndExpire(String key, long delta, long ttlMillis) {
                    return store.merge(key, delta, Long::sum);
                }

                public void set(String key, long value, long ttlMillis) {
                    store.put(key, value);
                }
            }
            """.formatted(method));
    }
}
