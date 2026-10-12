package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.TracerProvider;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * The error.type tag of a CompletionException must be its cause class, the same class the timer cache key uses,
 * so wrapped and plain errors of one class share a correctly labelled timer.
 */
class HttpServerMetricsErrorTypeTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DefaultHttpServerMetricsFactory.DefaultHttpServerMetrics metrics = DefaultHttpServerMetricsFactory.INSTANCE.create(context());

    private DefaultHttpServerTelemetry.TelemetryContext context() {
        var config = mock(HttpServerTelemetryConfig.class);
        when(config.metrics()).thenReturn(new HttpServerTelemetryConfig.HttpServerMetricsConfig() {});
        return new DefaultHttpServerTelemetry.TelemetryContext("test", 8080, config, false, true, registry,
            TracerProvider.noop().get("test"), new DefaultHttpServerBodyConverter());
    }

    private static HttpServerRequest request() {
        var request = mock(HttpServerRequest.class);
        when(request.method()).thenReturn("GET");
        when(request.pathTemplate()).thenReturn("/hello");
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("localhost");
        return request;
    }

    private void handle(Throwable error) {
        var request = request();
        metrics.recordStart(request);
        metrics.recordEnd(request, HttpServerResponse.of(500), error, 1000);
    }

    @Test
    void wrappedErrorIsReportedAsItsCause() {
        handle(new CompletionException(new IllegalStateException("boom")));

        assertThat(registry.find("http.server.request.duration").timers())
            .extracting(t -> t.getId().getTag("error.type"))
            .containsExactly("java.lang.IllegalStateException");
    }

    @Test
    void plainErrorAfterWrappedOneKeepsItsOwnType() {
        handle(new CompletionException(new IllegalStateException("boom")));
        handle(new IllegalStateException("plain"));

        assertThat(registry.find("http.server.request.duration").tag("error.type", "java.lang.IllegalStateException").timers())
            .extracting(Timer::count)
            .containsExactly(2L);
    }
}
