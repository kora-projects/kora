package ru.tinkoff.kora.opentelemetry.module.http.server;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.server.common.router.PublicApiHandler;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTracer;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.opentelemetry.context.Context.root;

public final class OpentelemetryHttpServerTracer implements HttpServerTracer {
    private final Tracer tracer;

    public OpentelemetryHttpServerTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public <T> void inject(Context context, T headers, HeadersSetter<T> headersSetter) {
        W3CTraceContextPropagator.getInstance().inject(
            OpentelemetryContext.get(context).getContext(),
            headers,
            headersSetter::set
        );
    }

    @Override
    public HttpServerSpan createSpan(String template, PublicApiHandler.PublicApiRequest routerRequest) {

        var context = Context.current();
        var parentCtx = W3CTraceContextPropagator.getInstance().extract(root(), routerRequest, PublicApiRequestTextMapGetter.INSTANCE);
        var span = this.tracer
            .spanBuilder(routerRequest.method() + " " + template)
            .setSpanKind(SpanKind.SERVER)
            .setParent(parentCtx)
            .setAttribute(SemanticAttributes.HTTP_METHOD, routerRequest.method())
            .setAttribute(SemanticAttributes.HTTP_SCHEME, routerRequest.scheme())
            .setAttribute(SemanticAttributes.NET_HOST_NAME, routerRequest.hostName())
            .setAttribute(SemanticAttributes.HTTP_TARGET, template)
            .startSpan();

        OpentelemetryContext.set(context, OpentelemetryContext.get(context).add(span));

        return (statusCode, resultCode, exception) -> {
            if (statusCode >= 500 || exception != null || resultCode != HttpResultCode.SUCCESS) {
                span.setStatus(StatusCode.ERROR);
            }
            if (exception != null) {
                span.recordException(exception);
            }
            span.end();
        };
    }

    private static class PublicApiRequestTextMapGetter implements TextMapGetter<PublicApiHandler.PublicApiRequest> {
        private static final PublicApiRequestTextMapGetter INSTANCE = new PublicApiRequestTextMapGetter();

        @Override
        public Iterable<String> keys(PublicApiHandler.PublicApiRequest carrier) {
            return () -> new Iterator<>() {
                final Iterator<Map.Entry<String, List<String>>> i = carrier.headers().iterator();

                @Override
                public boolean hasNext() {
                    return i.hasNext();
                }

                @Override
                public String next() {
                    return i.next().getKey();
                }
            };
        }

        @Override
        public String get(PublicApiHandler.PublicApiRequest carrier, String key) {
            return carrier.headers().getFirst(key);
        }
    }

}
