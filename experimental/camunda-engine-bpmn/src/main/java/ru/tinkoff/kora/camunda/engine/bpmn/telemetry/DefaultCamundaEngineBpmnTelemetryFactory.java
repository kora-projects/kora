package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.engine.bpmn.CamundaEngineBpmnConfig;

public final class DefaultCamundaEngineBpmnTelemetryFactory implements CamundaEngineBpmnTelemetryFactory {
    @Nullable
    private final MeterRegistry meterRegistry;
    @Nullable
    private final Tracer tracer;

    public DefaultCamundaEngineBpmnTelemetryFactory(@Nullable MeterRegistry meterRegistry, @Nullable Tracer tracer) {
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    @Override
    public CamundaEngineBpmnTelemetry get(CamundaEngineBpmnConfig.CamundaTelemetryConfig config) {
        if (!config.logging().enabled() && !config.tracing().enabled() && !config.metrics().enabled()) {
            return NoopCamundaEngineBpmnTelemetry.INSTANCE;
        }
        var meterRegistry = this.meterRegistry == null || !config.metrics().enabled()
            ? new CompositeMeterRegistry()
            : this.meterRegistry;
        var tracer = this.tracer == null || !config.tracing().enabled()
            ? TracerProvider.noop().get("nop")
            : this.tracer;

        return new DefaultCamundaEngineBpmnTelemetry(config, tracer, meterRegistry);
    }
}
