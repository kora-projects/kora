package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * The error.type tag of a CompletionException must be its cause class, the same class the timer cache key uses.
 */
class HttpClientMetricsErrorTypeTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DefaultHttpClientMetricsFactory.DefaultHttpClientMetrics metrics = DefaultHttpClientMetricsFactory.INSTANCE.create(
        new DefaultHttpClientTelemetry.TelemetryContext(DefaultHttpClientTelemetry.TelemetryContext.EMPTY.config(), false, true, registry,
            DefaultHttpClientTelemetryFactory.NOOP_TRACER, new DefaultHttpClientBodyConverter(List.of()), "httpClient.test", "foo.TestClient", "TestClient"));

    private static HttpClientRequest request() {
        var request = mock(HttpClientRequest.class);
        when(request.method()).thenReturn("GET");
        when(request.uri()).thenReturn(URI.create("http://localhost:8080/hello"));
        when(request.uriTemplate()).thenReturn("/hello");
        return request;
    }

    @Test
    void wrappedErrorIsReportedAsItsCause() {
        metrics.recordFailure(request(), new CompletionException(new HttpTimeoutException("timeout")), 1000);

        assertThat(registry.find("http.client.request.duration").timers())
            .extracting(t -> t.getId().getTag("error.type"))
            .containsExactly("java.net.http.HttpTimeoutException");
    }

    @Test
    void plainErrorAfterWrappedOneKeepsItsOwnType() {
        metrics.recordFailure(request(), new CompletionException(new HttpTimeoutException("timeout")), 1000);
        metrics.recordFailure(request(), new HttpTimeoutException("timeout"), 1000);

        assertThat(registry.find("http.client.request.duration").tag("error.type", "java.net.http.HttpTimeoutException").timers())
            .extracting(Timer::count)
            .containsExactly(2L);
    }
}
