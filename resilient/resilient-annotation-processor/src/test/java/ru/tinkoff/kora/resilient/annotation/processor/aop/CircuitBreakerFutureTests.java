package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;
import ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException;

import java.util.concurrent.CompletionException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CircuitBreakerFutureTests extends AppRunner {

    private CircuitBreakerTarget getService() {
        final InitializedGraph graph = getGraph(AppWithConfig.class,
            CircuitBreakerTarget.class,
            RetryTarget.class,
            TimeoutTarget.class,
            FallbackTarget.class);

        return getServiceFromGraph(graph, CircuitBreakerTarget.class);
    }

    @Test
    void stageCircuitBreaker() {
        // given
        final CircuitBreakerTarget service = getService();

        // when
        try {
            service.getValueStage().toCompletableFuture().join();
            fail("Should not happen");
        } catch (CompletionException e) {
            assertInstanceOf(IllegalStateException.class, e.getCause());
        }

        // then
        try {
            service.getValueStage().toCompletableFuture().join();
            fail("Should not happen");
        } catch (CompletionException e) {
            assertInstanceOf(CallNotPermittedException.class, e.getCause());
        }
    }

    @Test
    void futureCircuitBreaker() {
        // given
        final CircuitBreakerTarget service = getService();

        // when
        try {
            service.getValueFuture().join();
            fail("Should not happen");
        } catch (CompletionException e) {
            assertInstanceOf(IllegalStateException.class, e.getCause());
        }

        // then
        try {
            service.getValueFuture().join();
            fail("Should not happen");
        } catch (CompletionException e) {
            assertInstanceOf(CallNotPermittedException.class, e.getCause());
        }
    }
}
