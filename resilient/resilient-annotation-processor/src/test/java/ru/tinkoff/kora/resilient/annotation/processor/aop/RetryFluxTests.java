package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;
import ru.tinkoff.kora.resilient.retry.RetryExhaustedException;

import java.time.Duration;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetryFluxTests extends AppRunner {

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
    void fluxRetrySuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        assertEquals("1", service.retryFlux("1").blockFirst(Duration.ofMinutes(1)));
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void fluxRetryFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        try {
            service.retryFlux("1").blockFirst(Duration.ofMinutes(1));
            fail("Should not happen");
        } catch (RetryExhaustedException e) {
            assertNotNull(e.getMessage());
            assertEquals(2, service.getRetryAttempts());
        }
    }

    @Test
    void fluxRetryZeroSuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        assertEquals("1", service.retryFluxZeroAttempts("1").blockFirst(Duration.ofMinutes(1)));
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void fluxRetryZeroFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        assertThrows(IllegalStateException.class, () -> service.retryFluxZeroAttempts("1").blockFirst(Duration.ofMinutes(1)));
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void fluxRetryDisabledSuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        assertEquals("1", service.retryFluxDisabled("1").blockFirst(Duration.ofMinutes(1)));
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void fluxRetryDisabledFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        assertThrows(IllegalStateException.class, () -> service.retryFluxDisabled("1").blockFirst(Duration.ofMinutes(1)));
        assertEquals(0, service.getRetryAttempts());
    }
}
