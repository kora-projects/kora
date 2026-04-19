package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.telemetry.HttpClientObservation;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetry;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryConfig;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;

import java.net.URI;
import java.net.URISyntaxException;

public class DefaultHttpClientTelemetry implements HttpClientTelemetry {

    protected static final String SYSTEM_CONFIG = "system.name";
    protected static final String SYSTEM_IMPL = "system.impl";

    protected final String clientName;
    protected final String clientImpl;
    protected final String clientSimpleImpl;
    protected final HttpClientTelemetryConfig config;
    protected final Tracer tracer;
    protected final DefaultHttpClientLogger logger;
    protected final DefaultHttpClientMetrics metrics;

    public DefaultHttpClientTelemetry(String clientName,
                                      String clientImpl,
                                      HttpClientTelemetryConfig config,
                                      Tracer tracer,
                                      DefaultHttpClientLogger logger,
                                      DefaultHttpClientMetrics metrics) {
        this.clientName = clientName;
        this.clientImpl = clientImpl;
        this.clientSimpleImpl = clientImpl.substring(clientImpl.lastIndexOf('.') + 1);
        this.config = config;
        this.tracer = tracer;
        this.logger = logger;
        this.metrics = metrics;
    }

    @Override
    public HttpClientObservation observe(HttpClientRequest request) {
        var span = config.tracing().enabled()
            ? startSpan(request).startSpan()
            : Span.getInvalid();
        return new DefaultHttpClientObservation(this.config, this.logger, this.metrics, request, span);
    }

    protected SpanBuilder startSpan(HttpClientRequest request) {
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
            builder.setAttribute(ServerAttributes.SERVER_ADDRESS, targetUri.getHost())
                .setAttribute(ServerAttributes.SERVER_PORT, (long) targetUri.getPort())
                .setAttribute(UrlAttributes.URL_SCHEME, targetUri.getScheme())
                .setAttribute(UrlAttributes.URL_FULL, targetUri.toString());
        }
        builder.setAttribute(SYSTEM_CONFIG, clientName)
            .setAttribute(SYSTEM_IMPL, clientSimpleImpl);

        for (var entry : this.config.tracing().attributes().entrySet()) {
            builder.setAttribute(entry.getKey(), entry.getValue());
        }

        return builder;
    }

    protected String operation(String method, String uriTemplate, URI uri) {
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
