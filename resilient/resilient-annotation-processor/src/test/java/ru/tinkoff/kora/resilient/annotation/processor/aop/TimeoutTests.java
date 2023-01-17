package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;
import ru.tinkoff.kora.resilient.timeout.TimeoutException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimeoutTests extends AppRunner {

    private TimeoutTarget getService() {
        final InitializedGraph graph = getGraph(AppWithConfig.class,
            CircuitBreakerTarget.class,
            RetryableTarget.class,
            TimeoutTarget.class,
            FallbackTarget.class);

        return getServiceFromGraph(graph, TimeoutTarget.class);
    }

    @Test
    void syncTimeout() {
        // given
        var service = getService();

        assertThrows(TimeoutException.class, service::getValueSync);
    }

    @Test
    void monoTimeout() {
        // given
        var service = getService();

        // then
        assertThrows(RuntimeException.class, () -> service.getValueMono().block());
    }

    @Test
    void fluxTimeout() {
        // given
        var service = getService();

        // when
        assertThrows(RuntimeException.class, () -> service.getValueFlux().blockFirst());
    }
}
