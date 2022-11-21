package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.CircuitBreakerFallbackTarget;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.CircuitBreakerLifecycle;

import java.time.Duration;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CircuitBreakerFallbackTests extends CircuitBreakerRunner {

    private CircuitBreakerFallbackTarget getService(InitializedGraph graph) {
        var values = graph.graphDraw().getNodes()
            .stream()
            .map(graph.refreshableGraph()::get)
            .toList();

        return values.stream()
            .filter(a -> a instanceof CircuitBreakerLifecycle)
            .map(a -> ((CircuitBreakerLifecycle) a).fallbackTarget())
            .findFirst().orElseThrow();
    }

    @Test
    void syncCircuitBreaker() {
        // given
        var graphDraw = createGraphDraw();
        var service = getService(graphDraw);

        // when
        try {
            service.getValueSync();
            fail("Should not happen");
        } catch (IllegalStateException e) {
            assertNotNull(e.getMessage());
        }

        // then
        final String fallback = service.getValueSync();
        assertEquals(CircuitBreakerFallbackTarget.FALLBACK, fallback);
    }

    @Test
    void monoCircuitBreaker() {
        // given
        var graphDraw = createGraphDraw();
        var service = getService(graphDraw);

        // when
        try {
            service.getValueMono().block(Duration.ofSeconds(5));
            fail("Should not happen");
        } catch (IllegalStateException e) {
            assertNotNull(e.getMessage());
        }

        // then
        final String fallback = service.getValueMono().block(Duration.ofSeconds(5));
        assertEquals(CircuitBreakerFallbackTarget.FALLBACK, fallback);
    }

    @Test
    void fluxCircuitBreaker() {
        // given
        var graphDraw = createGraphDraw();
        var service = getService(graphDraw);

        // when
        try {
            service.getValueFlux().blockFirst(Duration.ofSeconds(5));
            fail("Should not happen");
        } catch (IllegalStateException e) {
            assertNotNull(e.getMessage());
        }

        // then
        final String fallback = service.getValueFlux().blockFirst(Duration.ofSeconds(5));
        assertEquals(CircuitBreakerFallbackTarget.FALLBACK, fallback);
    }
}
