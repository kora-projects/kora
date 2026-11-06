package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DefaultHttpServerMetricsFactoryTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DefaultHttpServerMetricsFactory.DefaultHttpServerMetrics metrics = DefaultHttpServerMetricsFactory.INSTANCE.create(context());

    private DefaultHttpServerTelemetry.TelemetryContext context() {
        var config = mock(HttpServerTelemetryConfig.class);
        when(config.metrics()).thenReturn(new HttpServerTelemetryConfig.HttpServerMetricsConfig() {});
        return new DefaultHttpServerTelemetry.TelemetryContext("test", 8080, config, false, true, registry,
            TracerProvider.noop().get("test"), new DefaultHttpServerBodyConverter());
    }

    private static HttpServerRequest request(String method, @Nullable String pathTemplate, String host) {
        var request = mock(HttpServerRequest.class);
        when(request.method()).thenReturn(method);
        when(request.pathTemplate()).thenReturn(pathTemplate);
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn(host);
        return request;
    }

    private void handle(HttpServerRequest request) {
        metrics.recordStart(request);
        metrics.recordEnd(request, HttpServerResponse.of(200), null, 1000);
    }

    @Test
    void hostHeaderDoesNotCreateNewMeters() {
        for (int i = 0; i < 50; i++) {
            handle(request("GET", "/hello", "attacker-" + i + ".example"));
        }

        var timers = registry.find("http.server.request.duration").timers();
        var gauges = registry.find("http.server.active_requests").gauges();
        assertThat(timers).hasSize(1);
        assertThat(gauges).hasSize(1);
        assertThat(timers.iterator().next().count()).isEqualTo(50);
        // tag sets documented in kora-docs metrics.md#http-server
        assertThat(timers.iterator().next().getId().getTags()).extracting(Tag::getKey).containsExactlyInAnyOrder(
            "server.name", "server.port", "http.request.method", "http.response.status_code", "http.route", "url.scheme", "error.type");
        assertThat(gauges.iterator().next().getId().getTags()).extracting(Tag::getKey).containsExactlyInAnyOrder(
            "server.name", "server.port", "http.request.method", "http.route", "url.scheme");
    }

    @Test
    void unknownMethodsAreReportedAsOther() {
        for (int i = 0; i < 30; i++) {
            handle(request("M" + i, null, "localhost"));
        }
        handle(request("GET", "/hello", "localhost"));

        var timers = registry.find("http.server.request.duration").timers();
        var gauges = registry.find("http.server.active_requests").gauges();
        assertThat(timers).hasSize(2);
        assertThat(gauges).hasSize(2);
        assertThat(registry.get("http.server.request.duration").tag("http.request.method", "_OTHER").timer().count()).isEqualTo(30);
        assertThat(registry.get("http.server.request.duration").tag("http.request.method", "GET").timer().count()).isEqualTo(1);
        assertThat(registry.get("http.server.active_requests").tag("http.request.method", "_OTHER").gauge().value()).isZero();
    }
}
