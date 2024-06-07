package ru.tinkoff.kora.opentelemetry.module.camunda.engine;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import ru.tinkoff.kora.camunda.engine.telemetry.CamundaEngineTracer;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

public final class OpentelemetryCamundaEngineTracer implements CamundaEngineTracer {

    private final Tracer tracer;

    public OpentelemetryCamundaEngineTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public CamundaEngineSpan createSpan(String javaDelegateName, DelegateExecution execution) {
        var context = Context.current();
        var span = this.tracer
            .spanBuilder("Camunda JavaDelegate " + javaDelegateName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("delegate", javaDelegateName)
            .setAttribute("eventName", execution.getEventName())
            .setAttribute("processBusinessKey", execution.getProcessBusinessKey())
            .setAttribute("processInstanceId", execution.getProcessInstanceId())
            .startSpan();

        OpentelemetryContext.set(context, OpentelemetryContext.get(context).add(span));

        return (exception) -> {
            if (exception != null) {
                span.setStatus(StatusCode.ERROR);
            }
            if (exception != null) {
                span.recordException(exception);
            }
            span.end();
        };
    }
}
