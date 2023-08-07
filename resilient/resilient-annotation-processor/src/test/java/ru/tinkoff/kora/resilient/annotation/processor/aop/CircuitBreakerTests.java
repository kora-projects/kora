package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;
import ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException;

import java.io.IOException;
import java.time.Duration;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CircuitBreakerTests extends AppRunner {

    private CircuitBreakerTarget getService() {
        final InitializedGraph graph = getGraph(AppWithConfig.class,
            CircuitBreakerTarget.class,
            RetryTarget.class,
            TimeoutTarget.class,
            FallbackTarget.class);

        return getServiceFromGraph(graph, CircuitBreakerTarget.class);
    }

    @Test
    void syncCircuitBreaker() {
        // given
        final CircuitBreakerTarget service = getService();

        // when
        try {
            service.getValueSync();
            fail("Should not happen");
        } catch (IllegalStateException e) {
            assertNotNull(e.getMessage());
        }

        // then
        try {
            service.getValueSync();
            fail("Should not happen");
        } catch (CallNotPermittedException ex) {
            assertNotNull(ex.getMessage());
        }
    }

    @Test
    void voidCircuitBreaker() {
        // given
        final CircuitBreakerTarget service = getService();

        // when
        try {
            service.getValueSyncVoid();
            fail("Should not happen");
        } catch (IllegalStateException e) {
            assertNotNull(e.getMessage());
        }

        // then
        try {
            service.getValueSyncVoid();
            fail("Should not happen");
        } catch (CallNotPermittedException ex) {
            assertNotNull(ex.getMessage());
        }
    }

    @Test
    void voidCircuitBreakerCheckedException() throws Exception {
        // given
        final CircuitBreakerTarget service = getService();

        // when
        try {
            service.getValueSyncVoidCheckedException();
            fail("Should not happen");
        } catch (IllegalStateException e) {
            assertNotNull(e.getMessage());
        }

        // then
        try {
            service.getValueSyncVoidCheckedException();
            fail("Should not happen");
        } catch (CallNotPermittedException ex) {
            assertNotNull(ex.getMessage());
        }
    }

    @Test
    void syncCircuitBreakerCheckedException() {
        // given
        final CircuitBreakerTarget service = getService();

        // when
        try {
            service.getValueSyncCheckedException();
            fail("Should not happen");
        } catch (IllegalStateException e) {
            assertNotNull(e.getMessage());
        } catch (IOException e) {
            fail(e);
        }

        // then
        try {
            service.getValueSyncCheckedException();
            fail("Should not happen");
        } catch (CallNotPermittedException ex) {
            assertNotNull(ex.getMessage());
        } catch (IOException e) {
            fail(e);
        }
    }

    @Test
    void monoCircuitBreaker() {
        // given
        final CircuitBreakerTarget service = getService();

        // when
        try {
            service.getValueMono().block(Duration.ofSeconds(5));
            fail("Should not happen");
        } catch (IllegalStateException e) {
            assertNotNull(e.getMessage());
        }

        // then
        try {
            service.getValueMono().block(Duration.ofSeconds(5));
            fail("Should not happen");
        } catch (CallNotPermittedException ex) {
            assertNotNull(ex.getMessage());
        }
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
