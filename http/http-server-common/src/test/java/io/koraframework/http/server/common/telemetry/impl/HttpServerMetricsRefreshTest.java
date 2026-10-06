package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.application.graph.ApplicationGraphDraw;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.TracerProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * UndertowHttpServerFactoryModule#handler builds a new HttpServerTelemetry whenever the httpServer config section changes.
 */
class HttpServerMetricsRefreshTest {

    @Test
    void activeRequestsGaugeTracksMetricsCreatedByConfigRefresh() throws Exception {
        var registry = new SimpleMeterRegistry();
        var draw = new ApplicationGraphDraw(HttpServerMetricsRefreshTest.class);
        var configNode = draw.addNode(HttpServerTelemetryConfig.class, null, null, List.of(), List.of(), List.of(), _ -> {
            var config = mock(HttpServerTelemetryConfig.class);
            when(config.metrics()).thenReturn(new HttpServerTelemetryConfig.HttpServerMetricsConfig() {});
            return config;
        });
        var metricsNode = draw.addNode(DefaultHttpServerMetricsFactory.DefaultHttpServerMetrics.class, null, null, List.of(configNode), List.of(configNode), List.of(),
            g -> DefaultHttpServerMetricsFactory.INSTANCE.create(new DefaultHttpServerTelemetry.TelemetryContext("test", 8080, g.get(configNode), false, true, registry,
                TracerProvider.noop().get("test"), new DefaultHttpServerBodyConverter())));
        var graph = draw.init();

        var request = mock(HttpServerRequest.class);
        when(request.method()).thenReturn("GET");
        when(request.pathTemplate()).thenReturn("/hello");
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("localhost");
        // one request before the refresh registers the gauge
        graph.get(metricsNode).recordStart(request);
        graph.get(metricsNode).recordEnd(request, io.koraframework.http.server.common.response.HttpServerResponse.of(200), null, 1000);

        graph.refresh(configNode);
        System.gc();

        // two requests in flight after the refresh
        graph.get(metricsNode).recordStart(request);
        graph.get(metricsNode).recordStart(request);
        assertThat(registry.get("http.server.active_requests").gauge().value()).as("http.server.active_requests").isEqualTo(2.0);
        graph.release();
    }
}
