package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;
import ru.tinkoff.kora.resilient.timeout.TimeoutExhaustedException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimeoutMonoTests extends AppRunner {

    private TimeoutTarget getService() {
        final InitializedGraph graph = getGraph(AppWithConfig.class,
            CircuitBreakerTarget.class,
            RetryTarget.class,
            TimeoutTarget.class,
            FallbackTarget.class);

        return getServiceFromGraph(graph, TimeoutTarget.class);
    }

    @Test
    void monoTimeout() {
        // given
        var service = getService();

        // then
        assertThrows(TimeoutExhaustedException.class, () -> service.getValueMono().block());
    }
}
