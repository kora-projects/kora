package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.HttpAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.UrlAttributes;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class DefaultHttpClientTelemetry implements HttpClientTelemetry {

    public record TelemetryContext(HttpClientTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer,
                                   DefaultHttpClientBodyConverter bodyLogger,
                                   String clientConfigPath,
                                   String clientCanonicalName,
                                   String clientSimpleName) {

        public static final TelemetryContext EMPTY = new TelemetryContext(new $HttpClientTelemetryConfig_ConfigValueExtractor.HttpClientTelemetryConfig_Impl(
            new $HttpClientTelemetryConfig_HttpClientLoggerConfig_ConfigValueExtractor.HttpClientLoggerConfig_Defaults(),
            new $HttpClientTelemetryConfig_HttpClientTracingConfig_ConfigValueExtractor.HttpClientTracingConfig_Defaults(),
            new $HttpClientTelemetryConfig_HttpClientMetricsConfig_ConfigValueExtractor.HttpClientMetricsConfig_Defaults()
        ), false, false, DefaultHttpClientTelemetryFactory.NOOP_METER_REGISTRY, DefaultHttpClientTelemetryFactory.NOOP_TRACER, new DefaultHttpClientBodyConverter(), "none", "none", "none");
    }

    public static final String SYSTEM_CONFIG_PATH = "system.path";
    public static final String SYSTEM_NAME_SIMPLE = "system.name.simple";
    public static final String SYSTEM_NAME_CANONICAL = "system.name.canonical";

    protected final TelemetryContext context;
    protected final DefaultHttpClientLoggerFactory.DefaultHttpClientLogger logger;
    protected final DefaultHttpClientMetricsFactory.DefaultHttpClientMetrics metrics;

    public DefaultHttpClientTelemetry(String clientConfigPath,
                                      String clientCanonicalName,
                                      HttpClientTelemetryConfig config,
                                      Tracer tracer,
                                      MeterRegistry meterRegistry,
                                      DefaultHttpClientMetricsFactory metricsFactory,
                                      DefaultHttpClientLoggerFactory loggerFactory,
                                      DefaultHttpClientBodyConverter loggerBodyConverter) {
        var isTraceEnabled = config.tracing().enabled() && tracer != DefaultHttpClientTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultHttpClientTelemetryFactory.NOOP_METER_REGISTRY;

        this.context = new TelemetryContext(config,
            isTraceEnabled,
            isMetricsEnabled,
            meterRegistry,
            tracer,
            loggerBodyConverter,
            clientConfigPath,
            clientCanonicalName,
            clientCanonicalName.substring(clientCanonicalName.lastIndexOf('.') + 1)
        );

        this.metrics = metricsFactory.create(this.context);
        this.logger = loggerFactory.create(this.context);
    }

    @Override
    public HttpClientObservation observe(HttpClientRequest request) {
        var span = this.context.config().tracing().enabled()
            ? startSpan(request).startSpan()
            : Span.getInvalid();
        return new DefaultHttpClientObservation(this.context, this.logger, this.metrics, request, span);
    }

    protected SpanBuilder startSpan(HttpClientRequest request) {
        var pathTemplate = getPathTemplate(request.uriTemplate(), request.uri());
        var builder = this.context.tracer.spanBuilder(request.method() + ' ' + pathTemplate)
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

        builder.setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, request.method())
            .setAttribute(HttpAttributes.HTTP_ROUTE, pathTemplate);
        if (targetUri != null) {
            builder.setAttribute(ServerAttributes.SERVER_ADDRESS, targetUri.getHost())
                .setAttribute(ServerAttributes.SERVER_PORT, (long) targetUri.getPort())
                .setAttribute(UrlAttributes.URL_SCHEME, targetUri.getScheme());

            if (this.context.config.tracing().pathFull()) {
                var path = targetUri.getPath();
                if (path != null) {
                    builder.setAttribute(UrlAttributes.URL_PATH, path);
                }
                builder.setAttribute(UrlAttributes.URL_FULL, targetUri.toString());
            }
        }

        if (request.body().contentLength() != -1) {
            var contentLength = String.valueOf(request.body().contentLength());
            builder.setAttribute(HttpAttributes.HTTP_REQUEST_HEADER.getAttributeKey("content-length"), List.of(contentLength));
        }

        builder.setAttribute(SYSTEM_CONFIG_PATH, this.context.clientConfigPath)
            .setAttribute(SYSTEM_NAME_SIMPLE, this.context.clientSimpleName)
            .setAttribute(SYSTEM_NAME_CANONICAL, this.context.clientCanonicalName);

        for (var entry : this.context.config().tracing().attributes().entrySet()) {
            builder.setAttribute(entry.getKey(), entry.getValue());
        }

        return builder;
    }

    protected String getPathTemplate(String uriTemplate, URI uri) {
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
