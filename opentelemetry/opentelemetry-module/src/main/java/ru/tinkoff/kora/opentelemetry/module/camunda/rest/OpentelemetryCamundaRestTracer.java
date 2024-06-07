package ru.tinkoff.kora.opentelemetry.module.camunda.rest;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.SemanticAttributes;
import io.undertow.util.HeaderMap;
import io.undertow.util.HttpString;
import ru.tinkoff.kora.camunda.rest.telemetry.CamundaRestTracer;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

import java.util.Iterator;

import static io.opentelemetry.context.Context.root;

public final class OpentelemetryCamundaRestTracer implements CamundaRestTracer {

    private final Tracer tracer;

    public OpentelemetryCamundaRestTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public CamundaRestSpan createSpan(String method, String path, HeaderMap headerMap) {

        var context = Context.current();
        var parentCtx = W3CTraceContextPropagator.getInstance().extract(root(), headerMap, PublicApiRequestTextMapGetter.INSTANCE);
        var span = this.tracer
            .spanBuilder(method + " " + path)
            .setSpanKind(SpanKind.SERVER)
            .setParent(parentCtx)
            .setAttribute(SemanticAttributes.HTTP_REQUEST_METHOD, method)
            .setAttribute(SemanticAttributes.URL_PATH, path)
            .setAttribute(SemanticAttributes.HTTP_ROUTE, path)
            .startSpan();

        OpentelemetryContext.set(context, OpentelemetryContext.get(context).add(span));

        return (statusCode, exception) -> {
            if (statusCode >= 400 || exception != null) {
                span.setStatus(StatusCode.ERROR);
            }
            if (exception != null) {
                span.recordException(exception);
            }
            if (statusCode >= 0) {
                span.setAttribute(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, statusCode);
            }
            span.end();
        };
    }

    private static class PublicApiRequestTextMapGetter implements TextMapGetter<HeaderMap> {
        private static final PublicApiRequestTextMapGetter INSTANCE = new PublicApiRequestTextMapGetter();

        @Override
        public Iterable<String> keys(HeaderMap carrier) {
            return () -> new Iterator<>() {
                final Iterator<HttpString> i = carrier.getHeaderNames().iterator();

                @Override
                public boolean hasNext() {
                    return i.hasNext();
                }

                @Override
                public String next() {
                    return i.next().toString();
                }
            };
        }

        @Override
        public String get(HeaderMap carrier, String key) {
            return carrier.getFirst(key);
        }
    }

}
