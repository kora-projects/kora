package ru.tinkoff.kora.opentelemetry.common;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.ImplicitContextKeyed;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.logging.common.MDC;

import javax.annotation.Nullable;

public class OpentelemetryContext {
    private static final Context.Key<OpentelemetryContext> KEY = new Context.Key<>() {
        @Override
        protected OpentelemetryContext copy(OpentelemetryContext object) {
            return new OpentelemetryContext(object.context);
        }
    };

    private final io.opentelemetry.context.Context context;

    public OpentelemetryContext() {
        this(io.opentelemetry.context.Context.root());
    }

    public OpentelemetryContext(io.opentelemetry.context.Context ctx) {
        this.context = ctx;
    }

    public static OpentelemetryContext get(Context ctx) {
        var tctx = ctx.get(KEY);
        if (tctx != null) {
            return tctx;
        }
        return ctx.set(KEY, new OpentelemetryContext());
    }

    public static void set(Context ctx, @Nullable OpentelemetryContext ot) {
        ctx.set(KEY, ot);
        var mdc = MDC.get();
        var span = ot != null ? Span.fromContextOrNull(ot.getContext()) : null;
        if (span == null) {
            mdc.remove0("traceId");
            mdc.remove0("spanId");
        } else {
            var spanContext = span.getSpanContext();
            mdc.put0("traceId", generator -> generator.writeString(spanContext.getTraceId()));
            mdc.put0("spanId", generator -> generator.writeString(spanContext.getSpanId()));
//            if (span instanceof ReadableSpan s) {
//                var parent = s.getParentSpanContext();
//                w.append("parentSpanId=")
//                    .append(parent.getSpanId())
//                    .append(" ");
//            } todo depends on tracing-api
        }
    }

    public OpentelemetryContext add(ImplicitContextKeyed value) {
        return new OpentelemetryContext(value.storeInContext(this.context));
    }

    public io.opentelemetry.context.Context getContext() {
        return context;
    }
}
