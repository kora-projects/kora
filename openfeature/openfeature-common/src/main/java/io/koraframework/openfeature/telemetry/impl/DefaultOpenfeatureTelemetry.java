package io.koraframework.openfeature.telemetry.impl;

import dev.openfeature.sdk.HookContext;
import io.koraframework.openfeature.telemetry.*;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;

public class DefaultOpenfeatureTelemetry implements OpenfeatureTelemetry {

    public record TelemetryContext(OpenfeatureTelemetryConfig config,
                                   boolean isTracingEnabled,
                                   boolean isMetricsEnabled,
                                   MeterRegistry meterRegistry,
                                   Tracer tracer,
                                   String clientConfigPath,
                                   String clientCanonicalName,
                                   String clientSimpleName) {

        public static final TelemetryContext EMPTY = new TelemetryContext(new $OpenfeatureTelemetryConfig_ConfigValueMapper.OpenfeatureTelemetryConfig_Impl(
            new $OpenfeatureTelemetryConfig_OpenfeatureLoggingConfig_ConfigValueMapper.OpenfeatureLoggingConfig_Defaults(),
            new $OpenfeatureTelemetryConfig_OpenfeatureMetricsConfig_ConfigValueMapper.OpenfeatureMetricsConfig_Defaults(),
            new $OpenfeatureTelemetryConfig_OpenfeatureTracingConfig_ConfigValueMapper.OpenfeatureTracingConfig_Defaults()
        ), false, false, DefaultOpenfeatureTelemetryFactory.NOOP_METER_REGISTRY, DefaultOpenfeatureTelemetryFactory.NOOP_TRACER, "none", "none", "none");
    }

    public static final String SYSTEM_CONFIG_PATH = "system.config";
    public static final String SYSTEM_NAME_SIMPLE = "system.name.simple";
    public static final String SYSTEM_NAME_CANONICAL = "system.name.canonical";

    protected final TelemetryContext context;
    protected final DefaultOpenfeatureLoggerFactory.DefaultOpenfeatureLogger logger;
    protected final DefaultOpenfeatureMetricsFactory.DefaultOpenfeatureMetrics metrics;

    public DefaultOpenfeatureTelemetry(String clientConfigPath,
                                       String clientCanonicalName,
                                       OpenfeatureTelemetryConfig config,
                                       Tracer tracer,
                                       MeterRegistry meterRegistry,
                                       DefaultOpenfeatureMetricsFactory metricsFactory,
                                       DefaultOpenfeatureLoggerFactory loggerFactory) {
        this.context = new TelemetryContext(config,
            config.tracing().enabled() && tracer != DefaultOpenfeatureTelemetryFactory.NOOP_TRACER,
            config.metrics().enabled() && meterRegistry != DefaultOpenfeatureTelemetryFactory.NOOP_METER_REGISTRY,
            meterRegistry, tracer, clientConfigPath, clientCanonicalName,
            clientCanonicalName.substring(clientCanonicalName.lastIndexOf('.') + 1));
        this.metrics = metricsFactory.create(this.context);
        this.logger = loggerFactory.create(this.context);
    }

    @Override
    public OpenfeatureObservation observe(HookContext<?> evaluation) {
        var span = this.context.isTracingEnabled() ? startSpan(evaluation).startSpan() : Span.getInvalid();
        try {
            return new DefaultOpenfeatureObservation(this.context, this.logger, this.metrics, evaluation, span);
        } catch (RuntimeException | Error e) {
            span.end();
            throw e;
        }
    }

    protected SpanBuilder startSpan(HookContext<?> evaluation) {
        var builder = this.context.tracer().spanBuilder("feature_flag.evaluate")
            .setSpanKind(SpanKind.INTERNAL)
            .setParent(Context.current())
            .setAttribute("feature_flag.key", evaluation.getFlagKey())
            .setAttribute("feature_flag.provider.name", evaluation.getProviderMetadata().getName())
            .setAttribute(SYSTEM_CONFIG_PATH, this.context.clientConfigPath())
            .setAttribute(SYSTEM_NAME_SIMPLE, this.context.clientSimpleName())
            .setAttribute(SYSTEM_NAME_CANONICAL, this.context.clientCanonicalName());
        this.context.config().tracing().attributes().forEach(builder::setAttribute);
        return builder;
    }
}
