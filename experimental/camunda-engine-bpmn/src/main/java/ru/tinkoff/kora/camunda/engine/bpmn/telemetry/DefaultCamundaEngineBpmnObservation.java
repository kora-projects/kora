package ru.tinkoff.kora.camunda.engine.bpmn.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import ru.tinkoff.kora.camunda.engine.bpmn.CamundaEngineBpmnConfig;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import java.util.concurrent.TimeUnit;

public class DefaultCamundaEngineBpmnObservation implements CamundaEngineObservation {
    protected final long start = System.nanoTime();
    protected final CamundaEngineBpmnConfig.CamundaTelemetryConfig config;
    protected final Span span;
    protected final Meter.MeterProvider<Timer> duration;
    protected final Logger logger;
    protected Throwable error;
    protected DelegateExecution execution;

    public DefaultCamundaEngineBpmnObservation(CamundaEngineBpmnConfig.CamundaTelemetryConfig config, Span span, Meter.MeterProvider<Timer> duration, Logger logger) {
        this.config = config;
        this.span = span;
        this.duration = duration;
        this.logger = logger;
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void end() {
        this.recordDuration();
        this.logEnd(this.execution);
        this.endSpan();
    }

    protected void endSpan() {
        this.span.end();
    }

    protected void recordDuration() {
        var took = System.nanoTime() - this.start;
        this.duration.withTag(ErrorAttributes.ERROR_TYPE.getKey(), this.error == null ? "" : this.error.getClass().getSimpleName())
            .record(took, TimeUnit.NANOSECONDS);
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
        this.span.setAttribute("eventName", execution.getEventName())
            .setAttribute("processBusinessKey", execution.getProcessBusinessKey())
            .setAttribute("processInstanceId", execution.getProcessInstanceId());
        this.logStart(execution);
    }

    protected void logStart(DelegateExecution execution) {
        logger
            .atInfo()
            .addKeyValue("camundaExecution", StructuredArgument.value(gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("processBusinessKey", execution.getProcessBusinessKey());
                gen.writeStringProperty("processInstanceId", execution.getProcessInstanceId());
                gen.writeStringProperty("activityId", execution.getCurrentActivityId());
                gen.writeStringProperty("activityName", execution.getCurrentActivityName());
                gen.writeStringProperty("eventName", execution.getEventName());
                gen.writeStringProperty("businessKey", execution.getBusinessKey());
                gen.writeEndObject();
            }))
            .log("Camunda BPMN Engine started");
    }

    protected void logEnd(DelegateExecution execution) {
        var data = StructuredArgument.value(gen -> {
            gen.writeStartObject();
            gen.writeStringProperty("processBusinessKey", execution.getProcessBusinessKey());
            gen.writeStringProperty("processInstanceId", execution.getProcessInstanceId());
            gen.writeStringProperty("activityId", execution.getCurrentActivityId());
            gen.writeStringProperty("activityName", execution.getCurrentActivityName());
            gen.writeStringProperty("eventName", execution.getEventName());
            gen.writeStringProperty("businessKey", execution.getBusinessKey());
            if (error != null) {
                var exceptionType = error.getClass().getCanonicalName();
                gen.writeStringProperty("exceptionType", exceptionType);
                if (!config.logging().stacktrace()) {
                    gen.writeStringProperty("exceptionMessage", error.getMessage());
                }
            }
            gen.writeEndObject();
        });
        this.logger.atLevel(this.error == null ? Level.INFO : Level.WARN)
            .addKeyValue("camundaExecution", data)
            .setCause(this.config.logging().stacktrace() ? this.error : null)
            .log(this.error == null
                ? "Camunda BPMN Engine finished delegate execution"
                : "Camunda BPMN Engine failed delegate execution"
            );
    }
}
