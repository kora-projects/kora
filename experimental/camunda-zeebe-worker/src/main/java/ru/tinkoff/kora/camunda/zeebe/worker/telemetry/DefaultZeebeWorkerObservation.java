package ru.tinkoff.kora.camunda.zeebe.worker.telemetry;

import io.camunda.zeebe.client.api.command.FailJobCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.ThrowErrorCommandStep1;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.semconv.ErrorAttributes;
import org.slf4j.Logger;
import ru.tinkoff.kora.camunda.zeebe.worker.JobWorkerException;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import java.util.concurrent.TimeUnit;

public class DefaultZeebeWorkerObservation implements ZeebeWorkerObservation {
    protected final Span span;
    private final Meter.MeterProvider<Timer> duration;
    protected final Logger logger;
    protected Throwable error;
    protected boolean failedByUser = false;
    protected final ActivatedJob job;

    public DefaultZeebeWorkerObservation(ActivatedJob job, Span span, Meter.MeterProvider<Timer> duration, Logger logger) {
        this.span = span;
        this.duration = duration;
        this.logger = logger;
        this.job = job;
    }

    @Override
    public void observeFinalCommandStep(FinalCommandStep<?> command) {
        if (command instanceof ThrowErrorCommandStep1.ThrowErrorCommandStep2 || command instanceof FailJobCommandStep1.FailJobCommandStep2) {
            this.failedByUser = true;
            this.span.setStatus(StatusCode.ERROR);
        }
    }

    @Override
    public void observeHandle(String type, ActivatedJob job) {
        this.logHandle(type, job);
    }

    protected void logHandle(String type, ActivatedJob job) {
        if (!logger.isInfoEnabled()) {
            return;
        }
        logger.atInfo()
            .addKeyValue("zeebeJob", StructuredArgument.value(gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("type", job.getType());
                gen.writeStringProperty("bpmnProcessId", job.getBpmnProcessId());
                gen.writeNumberProperty("key", job.getKey());
                gen.writeNumberProperty("processInstanceKey", job.getProcessInstanceKey());
                if (logger.isDebugEnabled()) {
                    gen.writeStringProperty("variables", job.getVariables());
                }
                gen.writeEndObject();
            }))
            .log("Zeebe JobWorker started Job");
    }

    @Override
    public Span span() {
        return this.span;
    }

    @Override
    public void observeError(Throwable e) {
        this.span.setStatus(StatusCode.ERROR);
        this.span.recordException(e);
        this.error = e;
    }

    @Override
    public void end() {
        this.writeMetrics();
        this.logEnd();
        this.endSpan();
    }

    protected void writeMetrics() {
        var took = System.nanoTime();
        final String errorType;
        if (error != null) {
            errorType = error.getClass().getSimpleName();
        } else if (failedByUser) {
            errorType = "ErrorStep";
        } else {
            errorType = "";
        }

        this.duration.withTag(ErrorAttributes.ERROR_TYPE.getKey(), errorType).record(took, TimeUnit.NANOSECONDS);
    }

    protected void endSpan() {
        this.span.end();
    }

    protected void logEnd() {
        var data = StructuredArgument.value(gen -> {
            gen.writeStartObject();
            gen.writeStringProperty("type", job.getType());
            gen.writeStringProperty("bpmnProcessId", job.getBpmnProcessId());
            gen.writeNumberProperty("key", job.getKey());
            gen.writeNumberProperty("processInstanceKey", job.getProcessInstanceKey());
            if (error instanceof JobWorkerException je) {
                gen.writeStringProperty("errorCode", je.getCode());
                gen.writeStringProperty("errorMessage", je.getMessage());
            }
            gen.writeEndObject();
        });
        if (error != null) {
            logger.atWarn()
                .setCause(error)
                .addKeyValue("zeebeJob", data)
                .log("Zeebe JobWorker failed Job");
        } else {
            logger.atInfo()
                .addKeyValue("zeebeJob", data)
                .log("Zeebe JobWorker completed Job");
        }
    }
}
