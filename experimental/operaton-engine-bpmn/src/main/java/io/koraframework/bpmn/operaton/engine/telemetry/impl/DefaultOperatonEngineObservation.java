package io.koraframework.bpmn.operaton.engine.telemetry.impl;

import io.koraframework.bpmn.operaton.engine.telemetry.OperatonEngineObservation;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.jspecify.annotations.Nullable;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

public class DefaultOperatonEngineObservation implements OperatonEngineObservation {

    protected final long start = System.nanoTime();
    protected final DefaultOperatonEngineTelemetry.TelemetryContext context;
    protected final Span span;
    protected final DefaultOperatonEngineLoggerFactory.DefaultOperatonEngineLogger logger;
    protected final DefaultOperatonEngineMetricsFactory.DefaultOperatonEngineMetrics metrics;

    @Nullable
    protected Throwable error;
    protected DelegateExecution execution;

    public DefaultOperatonEngineObservation(DefaultOperatonEngineTelemetry.TelemetryContext context,
                                            Span span,
                                            DefaultOperatonEngineLoggerFactory.DefaultOperatonEngineLogger logger,
                                            DefaultOperatonEngineMetricsFactory.DefaultOperatonEngineMetrics metrics) {
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
