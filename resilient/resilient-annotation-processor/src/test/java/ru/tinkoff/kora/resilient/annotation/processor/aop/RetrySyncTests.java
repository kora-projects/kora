package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;
import ru.tinkoff.kora.resilient.retry.RetryExhaustedException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetrySyncTests extends AppRunner {

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
    void syncVoidRetrySuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setRetryAttempts(RETRY_SUCCESS);

        // then
        service.retrySyncVoid("1");
    }

    @Test
    void syncVoidRetryFail() {
        // given
        var service = retryableTarget;

        // then
        service.setRetryAttempts(RETRY_FAIL);

        // then
        try {
            service.retrySyncVoid("1");
            fail("Should not happen");
        } catch (RetryExhaustedException ex) {
            assertNotNull(ex.getMessage());
        }
    }

    @Test
    void syncRetrySuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setRetryAttempts(RETRY_SUCCESS);

        // then
        assertEquals("1", service.retrySync("1"));
    }

    @Test
    void syncRetryFail() {
        // given
        var service = retryableTarget;

        // then
        service.setRetryAttempts(RETRY_FAIL);

        // then
        try {
            service.retrySync("1");
            fail("Should not happen");
        } catch (RetryExhaustedException ex) {
            assertNotNull(ex.getMessage());
        }
    }
}
