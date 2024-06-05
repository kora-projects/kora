package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;
import ru.tinkoff.kora.resilient.timeout.TimeoutExhaustedException;

import java.util.concurrent.CompletionException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimeoutFutureTests extends AppRunner {

    private TimeoutTarget getService() {
        final InitializedGraph graph = getGraph(AppWithConfig.class,
            CircuitBreakerTarget.class,
            RetryTarget.class,
            TimeoutTarget.class,
            FallbackTarget.class);

        return getServiceFromGraph(graph, TimeoutTarget.class);
    }

    @Test
    void stageTimeout() {
        // given
        var service = getService();

        // then
        var e = assertThrows(CompletionException.class, () -> service.getValueStage().toCompletableFuture().join());
        assertInstanceOf(TimeoutExhaustedException.class, e.getCause());
    }

    @Test
    void futureTimeout() {
        // given
        var service = getService();

        // then
        var e = assertThrows(CompletionException.class, () -> service.getValueFuture().join());
        assertInstanceOf(TimeoutExhaustedException.class, e.getCause());
    }
}
