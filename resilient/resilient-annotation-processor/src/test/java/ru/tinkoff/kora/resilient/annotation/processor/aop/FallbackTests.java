package ru.tinkoff.kora.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.aop.annotation.processor.AopAnnotationProcessor;
import ru.tinkoff.kora.resilient.annotation.processor.aop.testdata.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FallbackTests extends AppRunner {

    private FallbackTarget getService() {
        final InitializedGraph graph = getGraph(AppWithConfig.class,
            CircuitBreakerTarget.class,
            RetryableTarget.class,
            TimeoutTarget.class,
            FallbackTarget.class);

        return getServiceFromGraph(graph, FallbackTarget.class);
    }

    @Test
    void incorrectArgumentFallback() {
        assertThrows(TestUtils.CompilationErrorException.class, () -> TestUtils.annotationProcess(FallbackIncorrectArgumentTarget.class, new AopAnnotationProcessor()));
    }

    @Test
    void incorrectSignatureFallback() {
        assertThrows(TestUtils.CompilationErrorException.class, () -> TestUtils.annotationProcess(FallbackIncorrectSignatureTarget.class, new AopAnnotationProcessor()));
    }

    @Test
    void syncFallback() {
        // given
        var service = getService();
        service.alwaysFail = false;

        // when
        assertEquals(FallbackTarget.VALUE, service.getValueSync());
        service.alwaysFail = true;

        // then
        assertEquals(FallbackTarget.FALLBACK, service.getValueSync());
    }

    @Test
    void monoFallback() {
        // given
        var service = getService();
        service.alwaysFail = false;

        // when
        assertEquals(FallbackTarget.VALUE, service.getValueMono().block());
        service.alwaysFail = true;

        // then
        assertEquals(FallbackTarget.FALLBACK, service.getValueMono().block());
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
