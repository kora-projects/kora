package ru.tinkoff.kora.opentelemetry.module.scheduling;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingTracer;

import java.util.Map;

public class OpentelemetrySchedulingTracer implements SchedulingTracer {
    private final Tracer tracer;
    private final String className;
    private final String methodName;
    private final Map<String, String> attrs;

    public OpentelemetrySchedulingTracer(Tracer tracer, String className, String methodName, Map<String, String> attrs) {
        this.tracer = tracer;
        this.className = className;
        this.methodName = methodName;
        this.attrs = attrs;
    }


    @Override
    public SchedulingSpan createSpan(Context ctx) {
        var context = Context.current();
        var spanBuilder = this.tracer
            .spanBuilder(this.className + " " + this.methodName)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(CodeIncubatingAttributes.CODE_FUNCTION, this.methodName)
            .setAttribute(CodeIncubatingAttributes.CODE_FILEPATH, this.className);

        attrs.forEach(spanBuilder::setAttribute);

        var span = spanBuilder.startSpan();
        OpentelemetryContext.set(context, OpentelemetryContext.get(context).add(span));

        return (exception) -> {
            if (exception != null) {
                span.recordException(exception);
                span.setStatus(StatusCode.ERROR);
            } else {
                span.setStatus(StatusCode.OK);
            }
            span.end();
        };
    }
}
