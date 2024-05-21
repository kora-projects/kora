package ru.tinkoff.kora.camunda.engine.telemetry;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.delegate.DelegateExecution;

public final class DefaultCamundaEngineTelemetry implements CamundaEngineTelemetry {

    private final CamundaEngineMetrics metrics;
    private final CamundaEngineLogger logger;
    private final CamundaEngineTracer tracer;

    public DefaultCamundaEngineTelemetry(@Nullable CamundaEngineMetrics metrics,
                                         @Nullable CamundaEngineLogger logger,
                                         @Nullable CamundaEngineTracer tracer) {
        this.metrics = metrics;
        this.logger = logger;
        this.tracer = tracer;
    }

    @Override
    public CamundaEngineTelemetryContext get(String javaDelegateName, DelegateExecution execution) {
        var start = System.nanoTime();
        if (metrics != null) {
            metrics.executionStarted(javaDelegateName, execution);
        }

        final CamundaEngineTracer.CamundaEngineSpan span;
        if (tracer != null) {
            span = tracer.createSpan(javaDelegateName, execution);
        } else {
            span = null;
        }
        if (logger != null) {
            logger.logStart(javaDelegateName, execution);
        }

        return (exception) -> {
            var end = System.nanoTime();
            var processingTime = end - start;
            if (metrics != null) {
                metrics.executionFinished(javaDelegateName, execution, processingTime, exception);
            }
            if (logger != null) {
                logger.logEnd(javaDelegateName, execution, processingTime, exception);
            }
            if (span != null) {
                span.close(exception);
            }
        };
    }
}
