package io.koraframework.common.telemetry;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Kora carries the OpenTelemetry context in a {@link ScopedValue}, so the imperative
 * {@code makeCurrent()} / {@code attach()} pair cannot be implemented and throws. Everything therefore
 * depends on {@code wrap(...)}, which only {@link OpentelemetryContext} implements -- a context that
 * loses the wrapper falls back to the default {@code wrap(...)} and blows up.
 */
class OpentelemetryContextTest {

    private static final ContextKey<String> KEY = ContextKey.named("kora-test");

    @Test
    void aContextDerivedWithAKeyKeepsTheKoraImplementation() {
        var derived = Context.root().with(KEY, "value");

        var seen = new AtomicReference<String>();
        assertThatCode(() -> derived.wrap(() -> seen.set(Context.current().get(KEY))).run())
                .doesNotThrowAnyException();
        assertThat(seen.get()).isEqualTo("value");
    }

    /**
     * What {@code InstrumentationUtil.suppressInstrumentation} does, spelled out because it is an internal
     * class. The OTLP exporter runs every export through it on the BatchSpanProcessor worker thread, so
     * before the fix this threw IllegalStateException and no span ever left the application.
     */
    @Test
    void theExporterSuppressionPathDoesNotThrow() {
        var suppress = ContextKey.<Boolean>named("suppress_instrumentation");
        var ran = new AtomicReference<>(false);

        assertThatCode(() -> Context.current().with(suppress, true).wrap(() -> ran.set(true)).run())
                .doesNotThrowAnyException();
        assertThat(ran.get()).isTrue();
    }

    @Test
    void theStorageIsKoras() {
        assertThat(Context.root()).isInstanceOf(OpentelemetryContext.class);
    }
}
