package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;
import ru.tinkoff.kora.resilient.retry.RetryExhaustedException;

import java.io.IOException;

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

    private static final int RETRY_SUCCESS = 0;
    private static final int RETRY_FAIL = 3;

    private final RetryTarget retryableTarget = getService();

    @Test
    void syncVoidRetrySuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        service.retrySyncVoid("1");
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void syncVoidRetryFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        try {
            service.retrySyncVoid("1");
            fail("Should not happen");
        } catch (RetryExhaustedException ex) {
            assertNotNull(ex.getMessage());
            assertEquals(2, service.getRetryAttempts());
        }
    }

    @Test
    void syncRetrySuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        assertEquals("1", service.retrySync("1"));
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void syncRetryFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        try {
            service.retrySync("1");
            fail("Should not happen");
        } catch (RetryExhaustedException ex) {
            assertNotNull(ex.getMessage());
            assertEquals(2, service.getRetryAttempts());
        }
    }

    @Test
    void syncRetryCheckedSuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        try {
            service.retrySyncCheckedException("1");
            assertEquals(0, service.getRetryAttempts());
        } catch (IOException e) {
            fail("Should not happen");
        }
    }

    @Test
    void syncRetryCheckedFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        try {
            service.retrySyncCheckedException("1");
            fail("Should not happen");
        } catch (RetryExhaustedException ex) {
            assertNotNull(ex.getMessage());
            assertEquals(2, service.getRetryAttempts());
        } catch (IOException e) {
            fail("Should not happen");
        }
    }

    @Test
    void syncRetryZeroSuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        service.retrySyncZeroAttempts("1");
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void syncRetryZeroFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        assertThrows(IllegalStateException.class, () -> service.retrySyncZeroAttempts("1"));
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void syncRetryDisabledSuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        service.retrySyncDisabled("1");
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void syncRetryDisabledFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        assertThrows(IllegalStateException.class, () -> service.retrySyncDisabled("1"));
        assertEquals(0, service.getRetryAttempts());
    }
}
