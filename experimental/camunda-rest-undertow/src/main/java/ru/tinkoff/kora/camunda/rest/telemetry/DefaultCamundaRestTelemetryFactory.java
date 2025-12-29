package ru.tinkoff.kora.camunda.rest.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryConfig;
import ru.tinkoff.kora.http.server.common.telemetry.impl.DefaultHttpServerLogger;

public final class DefaultCamundaRestTelemetryFactory implements CamundaRestTelemetryFactory {
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    public DefaultCamundaRestTelemetryFactory(@Nullable MeterRegistry meterRegistry, @Nullable Tracer tracer) {
        if (meterRegistry == null) {
            meterRegistry = new CompositeMeterRegistry();
        }
        if (tracer == null) {
            tracer = TracerProvider.noop().get("http-server");
        }
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    @Override
    public CamundaRestTelemetry get(HttpServerTelemetryConfig config) {
        if (!config.metrics().enabled() && !config.tracing().enabled() && !config.logging().enabled()) {
            return NoopCamundaRestTelemetry.INSTANCE;
        }

        return new DefaultCamundaRestTelemetry(config, tracer, new DefaultHttpServerLogger(config.logging()), meterRegistry);
    }
}
