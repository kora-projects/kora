package io.koraframework.soap.client.common.telemetry.impl;

import io.koraframework.soap.client.common.SoapMethodDescriptor;
import io.koraframework.soap.client.common.envelope.SoapEnvelope;
import io.koraframework.soap.client.common.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;

import java.net.URI;

public class DefaultSoapClientTelemetry implements SoapClientTelemetry {

    public record TelemetryContext(SoapClientTelemetryConfig config,
                                   boolean isTracingEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer,
                                   String clientConfigPath,
                                   String clientCanonicalName,
                                   String clientSimpleName,
                                   SoapMethodDescriptor descriptor,
                                   String url) {

        public static final TelemetryContext EMPTY = new TelemetryContext(
            new $SoapClientTelemetryConfig_ConfigValueExtractor.SoapClientTelemetryConfig_Impl(
                new $SoapClientTelemetryConfig_SoapClientLoggingConfig_ConfigValueExtractor.SoapClientLoggingConfig_Defaults(),
                new $SoapClientTelemetryConfig_SoapClientMetricsConfig_ConfigValueExtractor.SoapClientMetricsConfig_Defaults(),
                new $SoapClientTelemetryConfig_SoapClientTracingConfig_ConfigValueExtractor.SoapClientTracingConfig_Defaults()
            ), false, false, DefaultSoapClientTelemetryFactory.NOOP_METER_REGISTRY, DefaultSoapClientTelemetryFactory.NOOP_TRACER, "none", "none", "none", new SoapMethodDescriptor("none", "none", "none", null), "http://localhost");
    }

    public static final String SYSTEM_CONFIG_PATH = "system.config";
    public static final String SYSTEM_NAME_SIMPLE = "system.name.simple";
    public static final String SYSTEM_NAME_CANONICAL = "system.name.canonical";

    protected final TelemetryContext context;
    protected final DefaultSoapClientLoggerFactory.DefaultSoapClientLogger logger;
    protected final DefaultSoapClientMetricsFactory.DefaultSoapClientMetrics metrics;

    public DefaultSoapClientTelemetry(String clientConfigPath,
                                      String clientCanonicalName,
                                      SoapClientTelemetryConfig config,
                                      SoapMethodDescriptor descriptor,
                                      String url,
                                      Tracer tracer,
                                      MeterRegistry meterRegistry,
                                      DefaultSoapClientMetricsFactory metricsFactory,
                                      DefaultSoapClientLoggerFactory loggerFactory) {
        var isTracingEnabled = config.tracing().enabled() && tracer != DefaultSoapClientTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultSoapClientTelemetryFactory.NOOP_METER_REGISTRY;

        this.context = new TelemetryContext(
            config,
            isTracingEnabled,
            isMetricsEnabled,
            meterRegistry,
            tracer,
            clientConfigPath,
            clientCanonicalName,
            clientCanonicalName.substring(clientCanonicalName.lastIndexOf('.') + 1),
            descriptor,
            url
        );
        this.metrics = metricsFactory.create(this.context);
        this.logger = loggerFactory.create(this.context);
    }

    @Override
    public SoapClientObservation observe(SoapEnvelope requestEnvelope) {
        var span = this.context.isTracingEnabled()
            ? this.startSpan().startSpan()
            : Span.getInvalid();
        return new DefaultSoapClientObservation(requestEnvelope, this.context, span, this.logger, this.metrics);
    }

    protected SpanBuilder startSpan() {
        var descriptor = this.context.descriptor();
        var builder = this.context.tracer().spanBuilder("SOAP " + descriptor.service() + " " + descriptor.method())
            .setSpanKind(SpanKind.CLIENT)
            .setParent(io.opentelemetry.context.Context.current())
            .setAttribute(RpcIncubatingAttributes.RPC_SERVICE, descriptor.service())
            .setAttribute(RpcIncubatingAttributes.RPC_METHOD, descriptor.method())
            .setAttribute(RpcIncubatingAttributes.RPC_SYSTEM, "soap")
            .setAttribute(SYSTEM_CONFIG_PATH, this.context.clientConfigPath())
            .setAttribute(SYSTEM_NAME_SIMPLE, this.context.clientSimpleName())
            .setAttribute(SYSTEM_NAME_CANONICAL, this.context.clientCanonicalName());

        var uri = URI.create(this.context.url());
        builder.setAttribute(ServerAttributes.SERVER_ADDRESS, uri.getHost());
        var port = getPort(uri);
        if (port != -1) {
            builder.setAttribute(ServerAttributes.SERVER_PORT, (long) port);
        }

        for (var entry : this.context.config().tracing().attributes().entrySet()) {
            builder.setAttribute(entry.getKey(), entry.getValue());
        }

        return builder;
    }

    protected static int getPort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return switch (uri.getScheme()) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
        };
    }
}
