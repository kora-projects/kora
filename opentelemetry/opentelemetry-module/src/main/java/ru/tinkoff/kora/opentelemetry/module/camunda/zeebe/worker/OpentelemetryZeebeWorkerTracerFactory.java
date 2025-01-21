package ru.tinkoff.kora.opentelemetry.module.camunda.zeebe.worker;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerTracer;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class OpentelemetryZeebeWorkerTracerFactory implements ZeebeWorkerTracerFactory {

    private final Tracer tracer;

    public OpentelemetryZeebeWorkerTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    @Override
    public ZeebeWorkerTracer get(TelemetryConfig.TracingConfig config) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new OpentelemetryZeebeWorkerTracer(this.tracer);
        } else {
            return null;
        }
    }
}
