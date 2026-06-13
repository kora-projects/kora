package io.koraframework.scheduling.common.telemetry.impl;

import io.koraframework.scheduling.common.telemetry.SchedulingObservation;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetryConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.CodeAttributes;

import java.util.Objects;

public class DefaultSchedulingTelemetry implements SchedulingTelemetry {

    public record TelemetryContext(Class<?> jobClass,
                                   String jobMethod,
                                   String jobName,
                                   SchedulingTelemetryConfig config,
                                   boolean isTraceEnabled,
                                   boolean isMetricsEnabled,
                                   Tracer tracer,
                                   MeterRegistry meterRegistry) {}

    protected final Class<?> jobClass;
    protected final String jobMethod;
    protected final TelemetryContext context;
    protected final DefaultSchedulingLoggerFactory.DefaultSchedulingLogger logger;
    protected final DefaultSchedulingMetricsFactory.DefaultSchedulingMetrics metrics;

    public DefaultSchedulingTelemetry(Class<?> jobClass,
                                      String jobMethod,
                                      SchedulingTelemetryConfig config,
                                      boolean isTraceEnabled,
                                      boolean isMetricsEnabled,
                                      Tracer tracer,
                                      MeterRegistry meterRegistry,
                                      DefaultSchedulingLoggerFactory loggerFactory,
                                      DefaultSchedulingMetricsFactory metricsFactory) {
        this.jobClass = Objects.requireNonNull(jobClass);
        this.jobMethod = Objects.requireNonNull(jobMethod);
        this.context = new TelemetryContext(jobClass, jobMethod, jobClass.getCanonicalName() + "." + jobMethod, config, isTraceEnabled, isMetricsEnabled, tracer, meterRegistry);
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
        if (!this.context.isTraceEnabled()) {
            return Span.getInvalid();
        }

        var span = this.context.tracer()
            .spanBuilder(this.context.jobClass().getCanonicalName() + " " + this.context.jobMethod())
            .setSpanKind(SpanKind.INTERNAL)
            .setParent(io.opentelemetry.context.Context.current())
            .setAttribute(CodeAttributes.CODE_FUNCTION_NAME, this.context.jobName());
        for (var entry : this.context.config().tracing().attributes().entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }
        return span.startSpan();
    }
}
