package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;
import ru.tinkoff.kora.resilient.retry.RetryExhaustedException;

import java.time.Duration;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetryMonoTests extends AppRunner {

    private RetryTarget getService() {
        final InitializedGraph graph = getGraph(AppWithConfig.class,
            CircuitBreakerTarget.class,
            RetryTarget.class,
            TimeoutTarget.class,
            FallbackTarget.class);

        return getServiceFromGraph(graph, RetryTarget.class);
    }

    private static final int RETRY_SUCCESS = 0;
    private static final int RETRY_FAIL = 3;

    private final RetryTarget retryableTarget = getService();

    @Test
    void monoRetrySuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        assertEquals("1", service.retryMono("1").block(Duration.ofMinutes(1)));
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void monoRetryFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        try {
            service.retryMono("1").block(Duration.ofMinutes(1));
            fail("Should not happen");
        } catch (RetryExhaustedException e) {
            assertNotNull(e.getMessage());
            assertEquals(2, service.getRetryAttempts());
        }
    }

    @Test
    void monoRetryZeroSuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        assertEquals("1", service.retryMonoZeroAttempts("1").block(Duration.ofMinutes(1)));
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void monoRetryZeroFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        assertThrows(IllegalStateException.class, () -> service.retryMonoZeroAttempts("1").block(Duration.ofMinutes(1)));
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void monoRetryDisabledSuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        assertEquals("1", service.retryMonoDisabled("1").block(Duration.ofMinutes(1)));
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void monoRetryDisabledFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        assertThrows(IllegalStateException.class, () -> service.retryMonoDisabled("1").block(Duration.ofMinutes(1)));
        assertEquals(0, service.getRetryAttempts());
    }
}
