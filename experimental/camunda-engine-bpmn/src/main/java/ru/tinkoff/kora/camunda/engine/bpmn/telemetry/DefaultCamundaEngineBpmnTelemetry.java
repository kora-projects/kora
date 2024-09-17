package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;

import jakarta.annotation.Nullable;
import org.camunda.bpm.engine.delegate.DelegateExecution;

public final class DefaultCamundaEngineBpmnTelemetry implements CamundaEngineBpmnTelemetry {

    private final CamundaEngineBpmnMetrics metrics;
    private final CamundaEngineBpmnLogger logger;
    private final CamundaEngineBpmnTracer tracer;

    public DefaultCamundaEngineBpmnTelemetry(@Nullable CamundaEngineBpmnMetrics metrics,
                                             @Nullable CamundaEngineBpmnLogger logger,
                                             @Nullable CamundaEngineBpmnTracer tracer) {
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

        final CamundaEngineBpmnTracer.CamundaEngineSpan span;
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
