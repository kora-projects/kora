package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FallbackFutureTests extends AppRunner {

    private FallbackTarget getService() {
        final InitializedGraph graph = getGraph(AppWithConfig.class,
            CircuitBreakerTarget.class,
            RetryTarget.class,
            TimeoutTarget.class,
            FallbackTarget.class);

        return getServiceFromGraph(graph, FallbackTarget.class);
    }

    @Test
    void stageFallback() {
        // given
        var service = getService();
        service.alwaysFail = false;

        // when
        assertEquals(FallbackTarget.VALUE, service.getValueStage().toCompletableFuture().join());
        service.alwaysFail = true;

        // then
        assertEquals(FallbackTarget.FALLBACK, service.getValueStage().toCompletableFuture().join());
    }

    @Test
    void futureFallback() {
        // given
        var service = getService();
        service.alwaysFail = false;

        // when
        assertEquals(FallbackTarget.VALUE, service.getValueFuture().join());
        service.alwaysFail = true;

        // then
        assertEquals(FallbackTarget.FALLBACK, service.getValueFuture().join());
    }
}
