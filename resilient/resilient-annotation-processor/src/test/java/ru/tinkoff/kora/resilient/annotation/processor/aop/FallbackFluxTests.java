package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FallbackFluxTests extends AppRunner {

    private FallbackTarget getService() {
        final InitializedGraph graph = getGraph(AppWithConfig.class,
            CircuitBreakerTarget.class,
            RetryTarget.class,
            TimeoutTarget.class,
            FallbackTarget.class);

        return getServiceFromGraph(graph, FallbackTarget.class);
    }

    @Test
    void fluxFallback() {
        // given
        var service = getService();
        service.alwaysFail = false;

        // when
        assertEquals(FallbackTarget.VALUE, service.getValueFlux().blockFirst());
        service.alwaysFail = true;

        // then
        assertEquals(FallbackTarget.FALLBACK, service.getValueFlux().blockFirst());
    }
}
