package ru.tinkoff.kora.micrometer.module.scheduling;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.semconv.CodeAttributes;
import io.opentelemetry.semconv.ErrorAttributes;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetrics;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class OpentelemetrySchedulingMetrics implements SchedulingMetrics {
    private final Map<Class<? extends Throwable>, Timer> errorDuration = new ConcurrentHashMap<>();
    private final Timer successDuration;
    private final MeterRegistry meterRegistry;
    private final TelemetryConfig.MetricsConfig config;
    private final String className;
    private final String methodName;

    public OpentelemetrySchedulingMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config, String className, String methodName) {
        this.meterRegistry = meterRegistry;
        this.config = config;
        this.className = className;
        this.methodName = methodName;
        this.successDuration = duration(null);
    }

    private Timer duration(@Nullable Class<? extends Throwable> error) {
        var builder = Timer.builder("scheduling.job.duration")
            .serviceLevelObjectives(this.config.slo())
            .tag(CodeAttributes.CODE_FUNCTION_NAME.getKey(), this.methodName)
            .tag("code.class", this.className);

        if (error != null) {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), error.getCanonicalName());
        } else {
            builder.tag(ErrorAttributes.ERROR_TYPE.getKey(), "");
        }

        return builder.register(this.meterRegistry);
    }

    @Override
    public void record(long processingTimeNanos, @Nullable Throwable e) {
        if (e == null) {
            this.successDuration.record(processingTimeNanos, TimeUnit.NANOSECONDS);
        } else {
            this.errorDuration.computeIfAbsent(e.getClass(), this::duration).record(processingTimeNanos, TimeUnit.NANOSECONDS);
        }
    }
}
