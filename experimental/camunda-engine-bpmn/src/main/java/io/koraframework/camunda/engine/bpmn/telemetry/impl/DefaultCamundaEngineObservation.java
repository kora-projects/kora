package io.koraframework.camunda.engine.bpmn.telemetry.impl;

import io.koraframework.camunda.engine.bpmn.telemetry.CamundaEngineObservation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.jspecify.annotations.Nullable;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

public class DefaultCamundaEngineObservation implements CamundaEngineObservation {

    protected final long start = System.nanoTime();
    protected final DefaultCamundaEngineTelemetry.TelemetryContext context;
    protected final Span span;
    protected final DefaultCamundaEngineLoggerFactory.DefaultCamundaEngineLogger logger;
    protected final DefaultCamundaEngineMetricsFactory.DefaultCamundaEngineMetrics metrics;

    @Nullable
    protected Throwable error;
    protected DelegateExecution execution;

    public DefaultCamundaEngineObservation(DefaultCamundaEngineTelemetry.TelemetryContext context,
                                           Span span,
                                           DefaultCamundaEngineLoggerFactory.DefaultCamundaEngineLogger logger,
                                           DefaultCamundaEngineMetricsFactory.DefaultCamundaEngineMetrics metrics) {
        this.context = context;
        this.span = span;
        this.logger = logger;
        this.metrics = metrics;
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        var took = System.nanoTime() - this.start;
        this.metrics.record(this.error, took);
        this.logger.logEnd(this.execution, this.error, took);
        this.span.end();
    }

    @Override
    public void observeError(Throwable e) {
        this.error = e;
        this.span.setStatus(StatusCode.ERROR);
        this.span.recordException(e);
    }

    @Override
    public void observeExecution(DelegateExecution execution) {
        this.execution = execution;
        setNotNull("eventName", execution.getEventName());
        setNotNull("processBusinessKey", execution.getProcessBusinessKey());
        setNotNull("processInstanceId", execution.getProcessInstanceId());
        this.logger.logStart(execution);
    }

    protected void setNotNull(String name, String value) {
        if (value != null) {
            this.span.setAttribute(stringKey(name), value);
        }
    }
}
