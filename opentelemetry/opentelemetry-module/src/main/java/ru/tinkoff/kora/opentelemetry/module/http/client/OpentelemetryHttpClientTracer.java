package ru.tinkoff.kora.opentelemetry.module.http.client;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.semconv.SemanticAttributes;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTracer;
import ru.tinkoff.kora.http.common.header.MutableHttpHeaders;
import ru.tinkoff.kora.opentelemetry.common.OpentelemetryContext;

import java.net.URI;
import java.net.URISyntaxException;

public final class OpentelemetryHttpClientTracer implements HttpClientTracer {
    private final Tracer tracer;

    public OpentelemetryHttpClientTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public HttpClientSpan createSpan(Context ctx, HttpClientRequest request) {
        var otctx = OpentelemetryContext.get(ctx);
        var builder = this.tracer.spanBuilder(operation(request.method(), request.uriTemplate(), request.uri()))
            .setSpanKind(SpanKind.CLIENT)
            .setParent(otctx.getContext());

        var targetUri = request.uri();
        if (targetUri.getRawUserInfo() != null || targetUri.getRawQuery() != null) {
            try {
                targetUri = new URI(
                    targetUri.getScheme(),
                    null,
                    targetUri.getHost(),
                    targetUri.getPort(),
                    targetUri.getPath(),
                    null,
                    targetUri.getFragment()
                );
            } catch (URISyntaxException e) {
                targetUri = null;
            }
        }

        builder.setAttribute(SemanticAttributes.HTTP_REQUEST_METHOD, request.method());
        if (targetUri != null) {
            builder.setAttribute(SemanticAttributes.SERVER_ADDRESS, targetUri.getHost());
            builder.setAttribute(SemanticAttributes.SERVER_PORT, (long) targetUri.getPort());
            builder.setAttribute(SemanticAttributes.URL_SCHEME, targetUri.getScheme());
            builder.setAttribute(SemanticAttributes.URL_FULL, targetUri.toString());
        }
        var span = builder.startSpan();

        var newCtx = otctx.add(span);
        OpentelemetryContext.set(ctx, newCtx);
        W3CTraceContextPropagator.getInstance().inject(newCtx.getContext(), request.headers(), MutableHttpHeaders::set);

        return (code, exception) -> {
            span.setAttribute(SemanticAttributes.HTTP_RESPONSE_STATUS_CODE, code);
            if (exception != null) {
                span.recordException(exception);
                span.setStatus(StatusCode.ERROR);
            }
            span.end();
        };
    }

    private static String operation(String method, String uriTemplate, URI uri) {
        if (uri.getAuthority() != null) {
            if (uri.getScheme() != null) {
                uriTemplate = uriTemplate.replace(uri.getScheme() + "://" + uri.getAuthority(), "");
            }
        }
        var questionMark = uriTemplate.indexOf('?');
        if (questionMark >= 0) {
            uriTemplate = uriTemplate.substring(0, questionMark);
        }
        return method + " " + uriTemplate;
    }

}
