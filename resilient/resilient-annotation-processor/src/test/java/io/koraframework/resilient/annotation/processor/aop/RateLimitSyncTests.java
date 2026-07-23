package io.koraframework.resilient.annotation.processor.aop;

import io.koraframework.resilient.ratelimiter.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitSyncTests extends ResilientAopTestSupport {

    @Test
    void syncRateLimitFirstCallSucceeds() {
        var service = compileRateLimitTarget("""
            @RateLimited(TestRateLimiter.class)
            public String call() {
                return "OK";
            }
            """);

        assertEquals("OK", invoke(service, "call"));
    }

    @Test
    void syncRateLimitSecondCallExceedsLimit() {
        var service = compileRateLimitTarget("""
            @RateLimited(TestRateLimiter.class)
            public String call() {
                return "OK";
            }
            """);

        invoke(service, "call");

        assertThrows(RateLimitExceededException.class, () -> invoke(service, "call"));
    }

    @Test
    void voidRateLimitFirstCallSucceeds() {
        var service = compileRateLimitTarget("""
            @RateLimited(TestRateLimiter.class)
            public void call() {}
            """);

        assertDoesNotThrow(() -> invoke(service, "call"));
    }

    @Test
    void voidRateLimitSecondCallExceedsLimit() {
        var service = compileRateLimitTarget("""
            @RateLimited(TestRateLimiter.class)
            public void call() {}
            """);

        invoke(service, "call");

        assertThrows(RateLimitExceededException.class, () -> invoke(service, "call"));
    }

    private Object compileRateLimitTarget(String method) {
        return compileApp("""
            custom1 {
              limitForPeriod = 1
              limitRefreshPeriod = 1s
            }
            """, """
            @RateLimiterSpec("custom1")
            public interface TestRateLimiter extends io.koraframework.resilient.ratelimiter.RateLimiter {}
            """, """
            @Component
            @Root
            public class TestTarget {
                %s
            }
            """.formatted(method));
    }
}
