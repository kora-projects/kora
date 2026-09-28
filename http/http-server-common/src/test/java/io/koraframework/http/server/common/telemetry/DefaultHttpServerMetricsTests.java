package io.koraframework.http.server.common.telemetry;

import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerBodyConverter;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerMetricsFactory;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerTelemetry;
import io.koraframework.http.server.common.telemetry.impl.DefaultHttpServerTelemetryFactory;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DefaultHttpServerMetricsTests {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final DefaultHttpServerMetricsFactory.DefaultHttpServerMetrics metrics = DefaultHttpServerMetricsFactory.INSTANCE.create(context());

    @Test
    void requestDurationIsTaggedWithResponseStatusCode() {
        var request = request("/path/{id}");

        metrics.recordStart(request);
        metrics.recordEnd(request, HttpServerResponse.of(200), null, 1_000_000);
        metrics.recordStart(request);
        metrics.recordEnd(request, HttpServerResponse.of(503), null, 1_000_000);
        metrics.recordStart(request);
        metrics.recordEnd(request, HttpServerResponse.of(503), null, 1_000_000);

        assertThat(duration("200").count()).isEqualTo(1);
        assertThat(duration("503").count()).isEqualTo(2);
    }

    @Test
    void unroutedRequestIsTaggedWithItsStatusCode() {
        var request = request(null);

        metrics.recordStart(request);
        metrics.recordEnd(request, HttpServerResponse.of(404), null, 1_000_000);

        assertThat(registry.get("http.server.request.duration")
            .tag("http.route", "UNKNOWN_ROUTE")
            .tag("http.response.status_code", "404")
            .timer().count()).isEqualTo(1);
    }

    private Timer duration(String statusCode) {
        return registry.get("http.server.request.duration")
            .tag("http.route", "/path/{id}")
            .tag("http.response.status_code", statusCode)
            .timer();
    }

    private static HttpServerRequest request(String pathTemplate) {
        var request = mock(HttpServerRequest.class);
        when(request.method()).thenReturn("GET");
        when(request.pathTemplate()).thenReturn(pathTemplate);
        when(request.scheme()).thenReturn("http");
        when(request.host()).thenReturn("localhost");
        return request;
    }

    private DefaultHttpServerTelemetry.TelemetryContext context() {
        return new DefaultHttpServerTelemetry.TelemetryContext(
            "test-http-server",
            8080,
            new $HttpServerTelemetryConfig_ConfigValueMapper.HttpServerTelemetryConfig_Impl(
                new $HttpServerTelemetryConfig_HttpServerLoggingConfig_ConfigValueMapper.HttpServerLoggingConfig_Defaults(),
                new $HttpServerTelemetryConfig_HttpServerMetricsConfig_ConfigValueMapper.HttpServerMetricsConfig_Defaults(),
                new $HttpServerTelemetryConfig_HttpServerTracingConfig_ConfigValueMapper.HttpServerTracingConfig_Defaults()
            ),
            false,
            true,
            registry,
            DefaultHttpServerTelemetryFactory.NOOP_TRACER,
            new DefaultHttpServerBodyConverter()
        );
    }
}
