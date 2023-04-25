package ru.tinkoff.kora.opentelemetry.module.http.client;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestBuilder;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTracer;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

import javax.annotation.Nullable;

import static io.opentelemetry.context.Context.root;

public final class OpentelemetryHttpClientTracer implements HttpClientTracer {
    private final Tracer tracer;

    public OpentelemetryHttpClientTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public CreateSpanResult createSpan(Context ctx, HttpClientRequest request) {
        var otctx = OpentelemetryContext.get(ctx);
        var builder = this.tracer.spanBuilder(request.operation())
            .setSpanKind(SpanKind.CLIENT)
            .setParent(otctx.getContext());
        builder.setAttribute(SemanticAttributes.HTTP_METHOD, request.method());
        builder.setAttribute(SemanticAttributes.HTTP_URL, request.uriTemplate());
        var span = builder.startSpan();
        OpentelemetryContext.set(ctx, otctx.add(span));
        var processedRequest = inject(request.toBuilder(), span).build();

        return new CreateSpanResult(exception -> {
            if (exception != null) {
                span.setStatus(StatusCode.ERROR);
            }
            span.end();
        }, processedRequest);
    }


    public static HttpClientRequestBuilder inject(HttpClientRequestBuilder builder, @Nullable Span span) {
        if (span == null) {
            return builder;
        }
        W3CTraceContextPropagator.getInstance().inject(span.storeInContext(root()), builder, HttpClientRequestBuilder::header);
        return builder;
    }
}
