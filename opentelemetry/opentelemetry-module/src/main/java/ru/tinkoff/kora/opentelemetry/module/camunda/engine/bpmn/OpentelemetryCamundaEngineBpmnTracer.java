package ru.tinkoff.kora.opentelemetry.module.camunda.engine.bpmn;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import ru.tinkoff.kora.camunda.engine.bpmn.telemetry.CamundaEngineBpmnTracer;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

import java.util.Map;

public final class OpentelemetryCamundaEngineBpmnTracer implements CamundaEngineBpmnTracer {

    private final Tracer tracer;
    private final Map<String, String> attrs;

    public OpentelemetryCamundaEngineBpmnTracer(Tracer tracer, Map<String, String> attrs) {
        this.tracer = tracer;
        this.attrs = attrs;
    }

    @Override
    public CamundaEngineSpan createSpan(String javaDelegateName, DelegateExecution execution) {
        var context = Context.current();
        var spanBuilder = this.tracer
            .spanBuilder("Camunda Delegate " + javaDelegateName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute("delegate", javaDelegateName)
            .setAttribute("eventName", execution.getEventName())
            .setAttribute("processBusinessKey", execution.getProcessBusinessKey())
            .setAttribute("processInstanceId", execution.getProcessInstanceId());

        attrs.forEach(spanBuilder::setAttribute);

        var span = spanBuilder.startSpan();

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
