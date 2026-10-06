package io.koraframework.openfeature.telemetry;

import dev.openfeature.sdk.*;
import dev.openfeature.sdk.providers.memory.Flag;
import dev.openfeature.sdk.providers.memory.InMemoryProvider;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.openfeature.telemetry.impl.*;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenfeatureTelemetryTest {

    private final OpenFeatureAPI api = OpenFeatureAPI.createIsolated();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @AfterEach
    void close() {
        api.shutdown();
        registry.close();
    }

    private static OpenfeatureTelemetryConfig config(boolean logging, boolean metrics, boolean tracing) {
        return new OpenfeatureTelemetryConfig() {
            public OpenfeatureLoggingConfig logging() { return new OpenfeatureLoggingConfig() {
                public boolean enabled() { return logging; }
            }; }
            public OpenfeatureMetricsConfig metrics() { return new OpenfeatureMetricsConfig() {
                public boolean enabled() { return metrics; }
                public Map<String, String> tags() { return Map.of("service", "test"); }
                public Duration[] slo() { return new Duration[]{Duration.ofMillis(2)}; }
            }; }
            public OpenfeatureTracingConfig tracing() { return new OpenfeatureTracingConfig() {
                public boolean enabled() { return tracing; }
                public Map<String, String> attributes() { return Map.of("service", "test"); }
            }; }
        };
    }

    private void install(OpenfeatureTelemetry telemetry) {
        api.addHooks(new OpenfeatureTelemetryHook(telemetry));
        api.setProviderAndWait(new InMemoryProvider(Map.of("flag",
            Flag.<Boolean>builder().variant("enabled", true).defaultVariant("enabled").build())));
    }

    @Test
    void disabledTelemetryUsesSingleton() {
        var loggerFactory = mock(DefaultOpenfeatureLoggerFactory.class);
        var metricsFactory = mock(DefaultOpenfeatureMetricsFactory.class);
        var factory = new DefaultOpenfeatureTelemetryFactory(mock(Tracer.class), registry, loggerFactory, metricsFactory);
        assertThat(factory.get("openfeature", "test.Provider", config(false, false, false)))
            .isSameAs(NoopOpenfeatureTelemetry.INSTANCE);
        assertThat(new DefaultOpenfeatureTelemetryFactory(null, null, loggerFactory, metricsFactory)
            .get("openfeature", "test.Provider", config(false, true, true)))
            .isSameAs(NoopOpenfeatureTelemetry.INSTANCE);
        verifyNoInteractions(loggerFactory, metricsFactory);
        assertThat(NoopOpenfeatureTelemetry.INSTANCE.observe(mock(HookContext.class)))
            .isSameAs(NoopOpenfeatureObservation.INSTANCE);
    }

    @Test
    void telemetryRecordsSuccessAndFallbackAndEndsSpans() {
        var tracer = mock(Tracer.class);
        var builder = mock(SpanBuilder.class, RETURNS_SELF);
        var span = mock(Span.class, RETURNS_SELF);
        when(tracer.spanBuilder(anyString())).thenReturn(builder);
        when(builder.startSpan()).thenReturn(span);
        install(new DefaultOpenfeatureTelemetryFactory(tracer, registry, null, null)
            .get("openfeature", "test.Provider", config(false, true, true)));
        var parentSpan = Span.wrap(SpanContext.create("0123456789abcdef0123456789abcdef",
            "0123456789abcdef", TraceFlags.getSampled(), TraceState.getDefault()));
        var parent = new OpentelemetryContext(Context.current().with(parentSpan));
        ScopedValue.where(OpentelemetryContext.VALUE, parent).run(() -> {
            assertThat(api.getClient().getBooleanValue("flag", false, new ImmutableContext("secret-user"))).isTrue();
            assertThat(api.getClient().getBooleanValue("flag", false, new ImmutableContext("another-user"))).isTrue();
            assertThat(api.getClient().getBooleanValue("missing", false)).isFalse();
        });
        var success = registry.get("feature_flag.evaluation.duration").tag("error.type", "none").timer();
        assertThat(success.count()).isEqualTo(2);
        assertThat(success.getId().getTag("service")).isEqualTo("test");
        assertThat(success.getId().getTag("system.config")).isEqualTo("openfeature");
        assertThat(success.getId().getTag("system.name.canonical")).isEqualTo("test.Provider");
        assertThat(success.takeSnapshot().histogramCounts()).hasSize(1);
        assertThat(registry.get("feature_flag.evaluation.duration").tag("error.type", "FLAG_NOT_FOUND").timer().count()).isEqualTo(1);
        assertThat(registry.find("feature_flag.evaluation.duration").timers()).hasSize(2);
        verify(builder, times(3)).setParent(argThat(context -> Span.fromContext(context) == parentSpan));
        verify(builder, times(3)).setSpanKind(SpanKind.INTERNAL);
        verify(builder, times(3)).setAttribute("service", "test");
        verify(builder, never()).setAttribute(eq("feature_flag.result.value"), anyString());
        verify(builder, never()).setAttribute(eq("feature_flag.context.id"), anyString());
        verify(span, times(2)).setAttribute("feature_flag.result.variant", "enabled");
        verify(span).setAttribute("error.type", "FLAG_NOT_FOUND");
        verify(span, times(3)).end();
    }

    @Test
    void loggingOnlyUsesCustomLoggerAndSkipsMetricsFactory() {
        var loggerFactory = mock(DefaultOpenfeatureLoggerFactory.class);
        var metricsFactory = mock(DefaultOpenfeatureMetricsFactory.class);
        var logger = mock(DefaultOpenfeatureLoggerFactory.DefaultOpenfeatureLogger.class);
        when(loggerFactory.create(any())).thenReturn(logger);
        install(new DefaultOpenfeatureTelemetryFactory(null, null, loggerFactory, metricsFactory)
            .get("openfeature", "test.Provider", config(true, true, true)));
        assertThat(api.getClient().getBooleanValue("flag", false)).isTrue();
        assertThat(api.getClient().getBooleanValue("missing", false)).isFalse();
        verify(logger, times(2)).logEvaluation(any());
        verify(logger).logResult(any(), any(), anyLong());
        verify(logger).logError(any(), any(), any(), anyLong());
        verifyNoInteractions(metricsFactory);
        assertThat(registry.getMeters()).isEmpty();
    }

    @Test
    void customMetricsFactoryCanAddTagsWithoutCacheCollisions() {
        var calls = new AtomicInteger();
        var factory = new DefaultOpenfeatureMetricsFactory() {
            @Override
            public DefaultOpenfeatureMetrics create(DefaultOpenfeatureTelemetry.TelemetryContext context) {
                return new DefaultOpenfeatureMetrics(context) {
                    @Override
                    protected DurationKey createMetricDurationKey(HookContext<?> evaluation,
                                                                    FlagEvaluationDetails<?> result, Throwable error) {
                        calls.incrementAndGet();
                        return super.createMetricDurationKey(evaluation, result, error)
                            .withExtraTags(Tags.of("custom", evaluation.getCtx().getTargetingKey()));
                    }
                };
            }
        };
        install(new DefaultOpenfeatureTelemetryFactory(null, registry, null, factory)
            .get("openfeature", "test.Provider", config(false, true, false)));
        api.getClient().getBooleanValue("flag", false, new ImmutableContext("one"));
        api.getClient().getBooleanValue("flag", false, new ImmutableContext("two"));
        assertThat(calls.get()).isEqualTo(2);
        assertThat(registry.get("feature_flag.evaluation.duration").tag("custom", "one").timer().count()).isEqualTo(1);
        assertThat(registry.get("feature_flag.evaluation.duration").tag("custom", "two").timer().count()).isEqualTo(1);
    }

    @Test
    void applicationTelemetryReceivesSuccessAndSdkFallback() {
        var telemetry = mock(OpenfeatureTelemetry.class);
        var success = mock(OpenfeatureObservation.class);
        var failure = mock(OpenfeatureObservation.class);
        when(telemetry.observe(any())).thenReturn(success, failure);
        install(telemetry);
        api.getClient().getBooleanValue("flag", false);
        api.getClient().getBooleanValue("missing", false);
        verify(success).observeResult(argThat(details -> details.getErrorCode() == null && Boolean.TRUE.equals(details.getValue())));
        verify(success, never()).observeError(any());
        verify(success).end();
        verify(failure).observeError(any());
        verify(failure).observeResult(argThat(details -> details.getErrorCode() == ErrorCode.FLAG_NOT_FOUND && Boolean.FALSE.equals(details.getValue())));
        verify(failure).end();
    }

    @Test
    void customObservationEndsEvenWhenResultCallbackFails() {
        var observation = mock(OpenfeatureObservation.class);
        doThrow(new IllegalStateException("custom observer failed")).when(observation).observeResult(any());
        install(evaluation -> observation);
        assertThat(api.getClient().getBooleanValue("flag", false)).isTrue();
        verify(observation).end();
    }

    @Test
    void spanEndsOnceEvenWhenMetricsFail() {
        var evaluation = mock(HookContext.class);
        var span = mock(Span.class, RETURNS_SELF);
        var logger = mock(DefaultOpenfeatureLoggerFactory.DefaultOpenfeatureLogger.class);
        var metrics = mock(DefaultOpenfeatureMetricsFactory.DefaultOpenfeatureMetrics.class);
        var result = new FlagEvaluationDetails<Boolean>();
        result.setValue(true);
        doThrow(new IllegalStateException("metrics failed")).when(metrics).recordSuccess(any(), any(), anyLong());
        var observation = new DefaultOpenfeatureObservation(DefaultOpenfeatureTelemetry.TelemetryContext.EMPTY,
            logger, metrics, evaluation, span);
        observation.observeResult(result);
        assertThatThrownBy(observation::end).hasMessage("metrics failed");
        observation.end();
        verify(span).end();
        verify(metrics).recordSuccess(any(), any(), anyLong());
    }

    @Test
    void observationRecordsUnexpectedErrorWithoutResult() {
        var evaluation = mock(HookContext.class);
        var span = mock(Span.class, RETURNS_SELF);
        var logger = mock(DefaultOpenfeatureLoggerFactory.DefaultOpenfeatureLogger.class);
        var metrics = mock(DefaultOpenfeatureMetricsFactory.DefaultOpenfeatureMetrics.class);
        var error = new IllegalArgumentException("failure");
        var observation = new DefaultOpenfeatureObservation(DefaultOpenfeatureTelemetry.TelemetryContext.EMPTY,
            logger, metrics, evaluation, span);
        assertThat(observation.span()).isSameAs(span);
        observation.observeError(error);
        observation.end();
        verify(metrics).recordFailure(eq(evaluation), isNull(), eq(error), anyLong());
        verify(span).recordException(error);
        verify(span, atLeastOnce()).setStatus(StatusCode.ERROR);
        verify(span).setAttribute("error.type", "GENERAL");
        verify(span).end();
    }

    @Test
    void spanEndsWhenCustomLoggerFailsBeforeObservationIsReturned() {
        var tracer = mock(Tracer.class);
        var builder = mock(SpanBuilder.class, RETURNS_SELF);
        var span = mock(Span.class, RETURNS_SELF);
        when(tracer.spanBuilder(anyString())).thenReturn(builder);
        when(builder.startSpan()).thenReturn(span);
        var loggerFactory = mock(DefaultOpenfeatureLoggerFactory.class);
        var logger = mock(DefaultOpenfeatureLoggerFactory.DefaultOpenfeatureLogger.class);
        when(loggerFactory.create(any())).thenReturn(logger);
        doThrow(new IllegalStateException("logger failed")).when(logger).logEvaluation(any());
        var evaluation = mock(HookContext.class);
        when(evaluation.getFlagKey()).thenReturn("flag");
        when(evaluation.getProviderMetadata()).thenReturn(() -> "provider");
        var telemetry = new DefaultOpenfeatureTelemetryFactory(tracer, null, loggerFactory, null)
            .get("openfeature", "test.Provider", config(true, false, true));
        assertThatThrownBy(() -> telemetry.observe(evaluation)).hasMessage("logger failed");
        verify(span).end();
    }

    @Test
    void telemetryFactoryBuildCanBeOverridden() {
        var telemetry = mock(OpenfeatureTelemetry.class);
        var captured = new AtomicReference<String>();
        var factory = new DefaultOpenfeatureTelemetryFactory(null, null, null, null) {
            @Override
            protected OpenfeatureTelemetry build(String path, String name, OpenfeatureTelemetryConfig config,
                                                   Tracer tracer, io.micrometer.core.instrument.MeterRegistry registry,
                                                   DefaultOpenfeatureMetricsFactory metricsFactory, DefaultOpenfeatureLoggerFactory loggerFactory) {
                captured.set(path + ":" + name);
                assertThat(metricsFactory).isSameAs(NoopOpenfeatureMetricsFactory.INSTANCE);
                assertThat(loggerFactory).isSameAs(DefaultOpenfeatureLoggerFactory.INSTANCE);
                return telemetry;
            }
        };
        assertThat(factory.get("custom.path", "custom.Provider", config(true, false, false))).isSameAs(telemetry);
        assertThat(captured.get()).isEqualTo("custom.path:custom.Provider");
    }
}
