package ru.tinkoff.kora.camunda.engine.telemetry;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import ru.tinkoff.kora.camunda.engine.CamundaEngineConfig;

public final class DefaultCamundaEngineTelemetryFactory implements CamundaEngineTelemetryFactory {

    private static final CamundaEngineTelemetry EMPTY_TELEMETRY = new StubCamundaEngineTelemetry();
    private static final CamundaEngineTelemetry.CamundaEngineTelemetryContext EMPTY_CONTEXT = new StubCamundaEngineTelemetryContext();

    private final CamundaEngineLoggerFactory logger;
    private final CamundaEngineMetricsFactory metrics;
    private final CamundaEngineTracerFactory tracer;

    public DefaultCamundaEngineTelemetryFactory(@Nullable CamundaEngineLoggerFactory logger,
                                                @Nullable CamundaEngineMetricsFactory metrics,
                                                @Nullable CamundaEngineTracerFactory tracer) {
        this.logger = logger;
        this.metrics = metrics;
        this.tracer = tracer;
    }

    @Override
    public CamundaEngineTelemetry get(CamundaEngineConfig.CamundaTelemetryConfig config) {
        var metrics = this.metrics == null ? null : this.metrics.get(config.metrics());
        var logging = this.logger == null ? null : this.logger.get(config.logging());
        var tracer = this.tracer == null ? null : this.tracer.get(config.tracing());
        if (metrics == null && tracer == null && logger == null) {
            return EMPTY_TELEMETRY;
        }

        return new DefaultCamundaEngineTelemetry(metrics, logging, tracer);
    }

    private static final class StubCamundaEngineTelemetry implements CamundaEngineTelemetry {

        @Override
        public CamundaEngineTelemetryContext get(String javaDelegateName, DelegateExecution execution) {
            return EMPTY_CONTEXT;
        }
    }

    private static final class StubCamundaEngineTelemetryContext implements CamundaEngineTelemetry.CamundaEngineTelemetryContext {

        @Override
        public void close(@Nullable Throwable exception) {
            // do nothing
        }
    }
}
