package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public class DefaultZeebeWorkerTelemetryFactory implements ZeebeWorkerTelemetryFactory {
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final Tracer tracer;

    public DefaultZeebeWorkerTelemetryFactory(@Nullable MeterRegistry meterRegistry, @Nullable Tracer tracer) {
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    @Override
    public ZeebeWorkerTelemetry get(String workerType, TelemetryConfig config) {
        if (!config.logging().enabled() && !config.tracing().enabled() && !config.metrics().enabled()) {
            return NoopZeebeWorkerTelemetry.INSTANCE;
        }
        var meterRegistry = this.meterRegistry == null || !config.metrics().enabled()
            ? new CompositeMeterRegistry()
            : this.meterRegistry;
        var tracer = this.tracer == null || !config.tracing().enabled()
            ? TracerProvider.noop().get("zeebe-worker")
            : this.tracer;

        return new DefaultZeebeWorkerTelemetry(config, workerType, tracer, meterRegistry);
    }
}
