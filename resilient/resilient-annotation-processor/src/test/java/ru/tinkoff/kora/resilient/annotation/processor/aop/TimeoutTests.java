package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;
import ru.tinkoff.kora.resilient.timeout.TimeoutException;

import java.io.IOException;

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
    void syncTimeoutVoid() {
        // given
        var service = getService();

        assertThrows(TimeoutException.class, service::getValueSyncVoid);
    }

    @Test
    void syncTimeoutCheckedException() {
        // given
        var service = getService();

        assertThrows(TimeoutException.class, service::getValueSyncCheckedException);
    }

    @Test
    void syncTimeoutCheckedExceptionVoid() {
        // given
        var service = getService();

        assertThrows(TimeoutException.class, service::getValueSyncCheckedExceptionVoid);
    }

    @Test
    void syncTimeoutCheckedExceptionVoidFailed() {
        // given
        var service = getService();

        var ex = assertThrows(IOException.class, service::getValueSyncCheckedExceptionVoidFailed);
        assertEquals("OPS", ex.getMessage());
    }

    @Test
    void monoTimeout() {
        // given
        var service = getService();

        // then
        assertThrows(TimeoutException.class, () -> service.getValueMono().block());
    }

    @Test
    void fluxTimeout() {
        // given
        var service = getService();

        // when
        assertThrows(TimeoutException.class, () -> service.getValueFlux().blockFirst());
    }
}
