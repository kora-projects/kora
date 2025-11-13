package ru.tinkoff.kora.scheduling.common.telemetry;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.semconv.CodeAttributes;
import org.slf4j.Logger;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultSchedulingTelemetry implements SchedulingTelemetry {
    private final Class<?> jobClass;
    private final String jobMethod;
    private final TelemetryConfig config;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;
    private final Logger logger;
    private final ConcurrentMap<Tags, Timer> durationCache = new ConcurrentHashMap<>();

    public DefaultSchedulingTelemetry(Class<?> jobClass, String jobMethod, TelemetryConfig config, MeterRegistry meterRegistry, Tracer tracer, Logger logger) {
        this.jobClass = Objects.requireNonNull(jobClass);
        this.jobMethod = Objects.requireNonNull(jobMethod);
        this.config = config;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
        this.logger = logger;
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
        if (!this.config.tracing().enabled() && !this.config.metrics().enabled() && !this.config.logging().enabled()) {
            return NoopSchedulingObservation.INSTANCE;
        }

        var span = createSpan();
        var duration = createDuration();
        return new DefaultSchedulingObservation(this.jobClass, this.jobMethod, span, duration, this.logger);
    }

    protected Meter.MeterProvider<Timer> createDuration() {
        return tags -> durationCache.computeIfAbsent(Tags.of(tags), t -> {
            var builder = Timer.builder("scheduling.job.duration")
                .serviceLevelObjectives(this.config.metrics().slo())
                .tag(CodeAttributes.CODE_FUNCTION_NAME.getKey(), this.jobClass.getCanonicalName() + "." + jobMethod)
                .tags(t);
            for (var tag : this.config.metrics().tags().entrySet()) {
                builder.tag(tag.getValue(), tag.getValue());
            }

            return builder.register(this.meterRegistry);
        });
    }

    protected Span createSpan() {
        var span = this.tracer
            .spanBuilder(this.jobClass.getCanonicalName() + " " + this.jobMethod)
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(CodeAttributes.CODE_FUNCTION_NAME, this.jobClass.getCanonicalName() + "." + jobMethod);
        for (var entry : this.config.tracing().attributes().entrySet()) {
            span.setAttribute(entry.getKey(), entry.getValue());
        }
        return span.startSpan();
    }
}
