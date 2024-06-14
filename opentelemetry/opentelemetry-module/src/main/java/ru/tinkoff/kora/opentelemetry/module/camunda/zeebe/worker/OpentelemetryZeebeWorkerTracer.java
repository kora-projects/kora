package ru.tinkoff.kora.opentelemetry.module.camunda.zeebe.worker;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import ru.tinkoff.kora.camunda.zeebe.worker.JobContext;
import ru.tinkoff.kora.camunda.zeebe.worker.JobWorkerException;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerTracer;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

public final class OpentelemetryZeebeWorkerTracer implements ZeebeWorkerTracer {

    private final Tracer tracer;

    public OpentelemetryZeebeWorkerTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public ZeebeWorkerSpan createSpan(String workerType, JobContext jobContext) {
        var context = Context.current();
        var span = this.tracer
            .spanBuilder("Zeebe Worker " + workerType)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("jobType", jobContext.jobType())
            .setAttribute("jobName", jobContext.jobName())
            .setAttribute("jobKey", jobContext.jobKey())
            .setAttribute("jobWorker", jobContext.jobWorker())
            .setAttribute("processKey", jobContext.processDefinitionKey())
            .setAttribute("elementId", jobContext.elementId())
            .startSpan();

        OpentelemetryContext.set(context, OpentelemetryContext.get(context).add(span));

        return (errorType, exception) -> {
            if (errorType != null) {
                span.setAttribute("errorType", errorType.toString());
            }
            if (exception != null) {
                final String errorCode = (exception instanceof JobWorkerException je) ? je.getCode() : null;
                if (errorCode != null) {
                    span.setAttribute("errorCode", errorCode);
                }
                span.setStatus(StatusCode.ERROR);
                span.recordException(exception);
            }
            span.end();
        };
    }
}
