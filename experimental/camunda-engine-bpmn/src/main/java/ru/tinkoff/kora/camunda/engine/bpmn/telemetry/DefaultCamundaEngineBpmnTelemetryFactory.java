package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import ru.tinkoff.kora.camunda.engine.bpmn.CamundaEngineBpmnConfig;

public final class DefaultCamundaEngineBpmnTelemetryFactory implements CamundaEngineBpmnTelemetryFactory {

    private static final CamundaEngineBpmnTelemetry EMPTY_TELEMETRY = new StubCamundaEngineBpmnTelemetry();
    private static final CamundaEngineBpmnTelemetry.CamundaEngineTelemetryContext EMPTY_CONTEXT = new StubCamundaEngineTelemetryContext();

    private final CamundaEngineBpmnLoggerFactory logger;
    private final CamundaEngineBpmnMetricsFactory metrics;
    private final CamundaEngineBpmnTracerFactory tracer;

    public DefaultCamundaEngineBpmnTelemetryFactory(@Nullable CamundaEngineBpmnLoggerFactory logger,
                                                    @Nullable CamundaEngineBpmnMetricsFactory metrics,
                                                    @Nullable CamundaEngineBpmnTracerFactory tracer) {
        this.logger = logger;
        this.metrics = metrics;
        this.tracer = tracer;
    }

    @Override
    public CamundaEngineBpmnTelemetry get(CamundaEngineBpmnConfig.CamundaTelemetryConfig config) {
        var metrics = this.metrics == null ? null : this.metrics.get(config.metrics());
        var logging = this.logger == null ? null : this.logger.get(config.logging());
        var tracer = this.tracer == null ? null : this.tracer.get(config.tracing());
        if (metrics == null && tracer == null && logger == null) {
            return EMPTY_TELEMETRY;
        }

        return new DefaultCamundaEngineBpmnTelemetry(metrics, logging, tracer);
    }

    private static final class StubCamundaEngineBpmnTelemetry implements CamundaEngineBpmnTelemetry {

        @Override
        public CamundaEngineTelemetryContext get(String javaDelegateName, DelegateExecution execution) {
            return EMPTY_CONTEXT;
        }
    }

    private static final class StubCamundaEngineTelemetryContext implements CamundaEngineBpmnTelemetry.CamundaEngineTelemetryContext {

        @Override
        public void close(@Nullable Throwable exception) {
            // do nothing
        }
    }
}
