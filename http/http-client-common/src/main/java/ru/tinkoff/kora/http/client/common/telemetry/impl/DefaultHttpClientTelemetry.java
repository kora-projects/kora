package ru.tinkoff.kora.http.client.common.telemetry.impl;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientObservation;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetry;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryConfig;

import java.net.URI;
import java.net.URISyntaxException;

public class DefaultHttpClientTelemetry implements HttpClientTelemetry {
    private final HttpClientTelemetryConfig config;
    private final Tracer tracer;
    private final DefaultHttpClientLogger logger;
    private final DefaultHttpClientMetrics metrics;

    public DefaultHttpClientTelemetry(HttpClientTelemetryConfig config, Tracer tracer, DefaultHttpClientLogger logger, DefaultHttpClientMetrics metrics) {
        this.config = config;
        this.tracer = tracer;
        this.logger = logger;
        this.metrics = metrics;
    }

    @Override
    public HttpClientObservation observe(HttpClientRequest request) {
        var span = startSpan(request);
        return new DefaultHttpClientObservation(this.config, this.logger, this.metrics, request, span);
    }

    private Span startSpan(HttpClientRequest request) {
        if (tracer == null || !config.tracing().enabled()) {
            return Span.getInvalid();
        }
        var builder = this.tracer.spanBuilder(operation(request.method(), request.uriTemplate(), request.uri()))
            .setSpanKind(SpanKind.CLIENT)
            .setParent(io.opentelemetry.context.Context.current());

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

        builder.setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, request.method());
        if (targetUri != null) {
            builder.setAttribute(ServerAttributes.SERVER_ADDRESS, targetUri.getHost());
            builder.setAttribute(ServerAttributes.SERVER_PORT, (long) targetUri.getPort());
            builder.setAttribute(UrlAttributes.URL_SCHEME, targetUri.getScheme());
            builder.setAttribute(UrlAttributes.URL_FULL, targetUri.toString());
        }
        for (var entry : this.config.tracing().attributes().entrySet()) {
            builder.setAttribute(entry.getKey(), entry.getValue());
        }

        return builder.startSpan();
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

    private static String pathTemplate(String uriTemplate, URI uri) {
        if (uri.getAuthority() != null) {
            if (uri.getScheme() != null) {
                uriTemplate = uriTemplate.replace(uri.getScheme() + "://" + uri.getAuthority(), "");
            }
        }
        var questionMark = uriTemplate.indexOf('?');
        if (questionMark >= 0) {
            uriTemplate = uriTemplate.substring(0, questionMark);
        }
        return uriTemplate;
    }
}
