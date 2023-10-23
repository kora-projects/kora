package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.BeforeEach;
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

    private static final int RETRY_SUCCESS = 1;
    private static final int RETRY_FAIL = 5;

    private final RetryTarget retryableTarget = getService();

    @BeforeEach
    void setup() {
        retryableTarget.reset();
    }

    @Test
    void fluxRetrySuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setRetryAttempts(RETRY_SUCCESS);

        // then
        assertEquals("1", service.retryFlux("1").blockFirst(Duration.ofMinutes(1)));
    }

    @Test
    void fluxRetryFail() {
        // given
        var service = retryableTarget;

        // then
        service.setRetryAttempts(RETRY_FAIL);

        // then
        try {
            service.retryFlux("1").blockFirst(Duration.ofMinutes(1));
            fail("Should not happen");
        } catch (RetryExhaustedException e) {
            assertNotNull(e.getMessage());
        }
    }
}
