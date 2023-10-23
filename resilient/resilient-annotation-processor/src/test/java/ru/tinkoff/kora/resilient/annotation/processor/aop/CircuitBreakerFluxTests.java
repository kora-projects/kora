package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;
import ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException;

import java.time.Duration;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CircuitBreakerFluxTests extends AppRunner {

    private CircuitBreakerTarget getService() {
        final InitializedGraph graph = getGraph(AppWithConfig.class,
            CircuitBreakerTarget.class,
            RetryTarget.class,
            TimeoutTarget.class,
            FallbackTarget.class);

        return getServiceFromGraph(graph, CircuitBreakerTarget.class);
    }

    @Test
    void fluxCircuitBreaker() {
        // given
        final CircuitBreakerTarget service = getService();

        // when
        try {
            service.getValueFlux().blockFirst(Duration.ofSeconds(5));
            fail("Should not happen");
        } catch (IllegalStateException e) {
            assertNotNull(e.getMessage());
        }

        // then
        try {
            service.getValueFlux().blockFirst(Duration.ofSeconds(5));
            fail("Should not happen");
        } catch (CallNotPermittedException ex) {
            assertNotNull(ex.getMessage());
        }
    }
}
