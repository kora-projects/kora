package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;
import ru.tinkoff.kora.resilient.retry.RetryExhaustedException;

import java.util.concurrent.CompletionException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RetryFutureTests extends AppRunner {

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
    void stageRetrySuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        assertEquals("1", service.retryStage("1").toCompletableFuture().join());
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void stageRetryFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        try {
            service.retryStage("1").toCompletableFuture().join();
            fail("Should not happen");
        } catch (CompletionException e) {
            assertNotNull(e.getMessage());
            assertInstanceOf(RetryExhaustedException.class, e.getCause());
            assertInstanceOf(IllegalStateException.class, e.getCause().getCause());
            assertEquals(2, service.getRetryAttempts());
        }
    }

    @Test
    void futureRetrySuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        assertEquals("1", service.retryFuture("1").join());
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void futureRetryFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        try {
            service.retryFuture("1").join();
            fail("Should not happen");
        } catch (CompletionException e) {
            assertNotNull(e.getMessage());
            assertInstanceOf(RetryExhaustedException.class, e.getCause());
            assertInstanceOf(IllegalStateException.class, e.getCause().getCause());
            assertEquals(2, service.getRetryAttempts());
        }
    }

    @Test
    void stageRetryZeroSuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        assertEquals("1", service.retryStageZeroAttempts("1").toCompletableFuture().join());
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void stageRetryZeroFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        var ex = assertThrows(CompletionException.class, () -> service.retryStageZeroAttempts("1").toCompletableFuture().join());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void futureRetryZeroSuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        assertEquals("1", service.retryFutureZeroAttempts("1").join());
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void futureRetryZeroFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        var ex = assertThrows(CompletionException.class, () -> service.retryFutureZeroAttempts("1").join());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void stageRetryDisabledSuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        assertEquals("1", service.retryStageDisabled("1").toCompletableFuture().join());
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void stageRetryDisabledFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        var ex = assertThrows(CompletionException.class, () -> service.retryStageDisabled("1").toCompletableFuture().join());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void futureRetryDisabledSuccess() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_SUCCESS);

        // then
        assertEquals("1", service.retryFutureDisabled("1").join());
        assertEquals(0, service.getRetryAttempts());
    }

    @Test
    void futureRetryDisabledFail() {
        // given
        var service = retryableTarget;

        // then
        service.setFailAttempts(RETRY_FAIL);

        // then
        var ex = assertThrows(CompletionException.class, () -> service.retryFutureDisabled("1").join());
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertEquals(0, service.getRetryAttempts());
    }
}
