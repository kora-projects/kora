package ru.tinkoff.kora.opentelemetry.module.camunda.engine.bpmn;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import ru.tinkoff.kora.camunda.engine.bpmn.telemetry.CamundaEngineBpmnTracer;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

public final class OpentelemetryCamundaEngineBpmnTracer implements CamundaEngineBpmnTracer {

    private final Tracer tracer;

    public OpentelemetryCamundaEngineBpmnTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public CamundaEngineSpan createSpan(String javaDelegateName, DelegateExecution execution) {
        var context = Context.current();
        var span = this.tracer
            .spanBuilder("Camunda Delegate " + javaDelegateName)
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
                span.recordException(exception);
            } else {
                span.setStatus(StatusCode.OK);
            }
            span.end();
        };
    }
}
