package io.koraframework.scheduling.common.telemetry.impl;

import io.koraframework.scheduling.common.telemetry.SchedulingObservation;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.CodeAttributes;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class DefaultSchedulingTelemetry implements SchedulingTelemetry {

    public record TelemetryContext(Class<?> jobClass,
                                   String jobMethod,
                                   String jobName,
                                   @Nullable String jobConfigPath,
                                   String jobSimpleName,
                                   String jobCanonicalName,
                                   SchedulingTelemetryConfig config,
                                   boolean isTracingEnabled,
                                   boolean isMetricsEnabled,
                                   Tracer tracer,
                                   MeterRegistry meterRegistry) {}

    public static final String SYSTEM_CONFIG_PATH = "system.config";
    public static final String SYSTEM_NAME_SIMPLE = "system.name.simple";
    public static final String SYSTEM_NAME_CANONICAL = "system.name.canonical";

    protected final Class<?> jobClass;
    protected final String jobMethod;
    protected final TelemetryContext context;
    protected final DefaultSchedulingLoggerFactory.DefaultSchedulingLogger logger;
    protected final DefaultSchedulingMetricsFactory.DefaultSchedulingMetrics metrics;

    public DefaultSchedulingTelemetry(@Nullable String jobConfigPath,
                                      Class<?> jobClass,
                                      String jobMethod,
                                      SchedulingTelemetryConfig config,
                                      Tracer tracer,
                                      MeterRegistry meterRegistry,
                                      DefaultSchedulingLoggerFactory loggerFactory,
                                      DefaultSchedulingMetricsFactory metricsFactory) {
        var isTracingEnabled = config.tracing().enabled() && tracer != DefaultSchedulingTelemetryFactory.NOOP_TRACER;
        var isMetricsEnabled = config.metrics().enabled() && meterRegistry != DefaultSchedulingTelemetryFactory.NOOP_METER_REGISTRY;

        this.jobClass = Objects.requireNonNull(jobClass);
        this.jobMethod = Objects.requireNonNull(jobMethod);
        var jobCanonicalClassName = jobClass.getCanonicalName();
        if (jobCanonicalClassName == null) {
            jobCanonicalClassName = jobClass.getName();
        }
        var jobSimpleName = jobClass.getSimpleName() + "#" + jobMethod;
        var jobCanonicalName = jobCanonicalClassName + "#" + jobMethod;
        this.context = new TelemetryContext(jobClass, jobMethod, jobCanonicalName, jobConfigPath, jobSimpleName, jobCanonicalName, config, isTracingEnabled, isMetricsEnabled, tracer, meterRegistry);
        this.logger = loggerFactory.create(this.context);
        this.metrics = metricsFactory.create(this.context);
    }

    @Override
    public Class<?> jobClass() {
        return this.jobClass;
    }

    @Override
    public String jobMethod() {
        return this.jobMethod;
    }

    @Override
    public SchedulingObservation observe() {
        var span = createSpan();
        return new DefaultSchedulingObservation(this.context, span, this.logger, this.metrics);
    }

    protected Span createSpan() {
        if (!this.context.isTracingEnabled()) {
            return Span.getInvalid();
        }

        var span = this.context.tracer()
            .spanBuilder("scheduling " + this.context.jobCanonicalName())
            .setSpanKind(SpanKind.INTERNAL)
            .setParent(io.opentelemetry.context.Context.current())
            .setAttribute(CodeAttributes.CODE_FUNCTION_NAME, this.context.jobName())
            .setAttribute(SYSTEM_NAME_SIMPLE, this.context.jobSimpleName())
            .setAttribute(SYSTEM_NAME_CANONICAL, this.context.jobCanonicalName());

        if (this.context.jobConfigPath != null) {
            span = span.setAttribute(SYSTEM_CONFIG_PATH, this.context.jobConfigPath());
        }
        for (var entry : this.context.config().tracing().attributes().entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }
        return span.startSpan();
    }
}
