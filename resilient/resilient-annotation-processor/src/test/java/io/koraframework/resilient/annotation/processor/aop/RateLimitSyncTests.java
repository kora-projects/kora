package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;
import ru.tinkoff.kora.resilient.ratelimiter.RateLimitExceededException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RateLimitSyncTests extends AppRunner {

    private RateLimitTarget getService() {
        final InitializedGraph graph = getGraph(AppWithConfig.class,
            CircuitBreakerTarget.class,
            RetryTarget.class,
            TimeoutTarget.class,
            FallbackTarget.class,
            RateLimitTarget.class);

        return getServiceFromGraph(graph, RateLimitTarget.class);
    }

    @Test
    void syncRateLimitFirstCallSucceeds() {
        // given
        final RateLimitTarget service = getService();

        // when/then — first call within limitForPeriod=1 should succeed
        final String result = service.getValueSync();
        assertNotNull(result);
        assertEquals("OK", result);
    }

    @Test
    void syncRateLimitSecondCallExceedsLimit() {
        // given
        final RateLimitTarget service = getService();

        // when
        service.getValueSync();

        // then — second call in same period should be rejected
        try {
            service.getValueSync();
            fail("Should not happen");
        } catch (RateLimitExceededException ex) {
            assertNotNull(ex.getMessage());
        }
    }

    @Test
    void voidRateLimitFirstCallSucceeds() {
        // given
        final RateLimitTarget service = getService();

        // when/then
        assertDoesNotThrow(service::getValueSyncVoid);
    }

    @Test
    void voidRateLimitSecondCallExceedsLimit() {
        // given
        final RateLimitTarget service = getService();

        // when
        service.getValueSyncVoid();

        // then
        try {
            service.getValueSyncVoid();
            fail("Should not happen");
        } catch (RateLimitExceededException ex) {
            assertNotNull(ex.getMessage());
        }
    }
}
