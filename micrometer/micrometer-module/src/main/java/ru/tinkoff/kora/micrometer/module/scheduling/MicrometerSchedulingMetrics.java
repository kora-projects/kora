package ru.tinkoff.kora.micrometer.module.scheduling;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetrics;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public class MicrometerSchedulingMetrics implements SchedulingMetrics {
    private final DistributionSummary successDuration;

    public MicrometerSchedulingMetrics(MeterRegistry meterRegistry, TelemetryConfig.MetricsConfig config, String className, String methodName) {
        var builder = DistributionSummary.builder("scheduling.job.duration")
            .serviceLevelObjectives(config.slo(null))
            .baseUnit("milliseconds")
            .tag("code.function", methodName)
            .tag("code.class", className);
        this.successDuration = builder.register(meterRegistry);
    }

    @Override
    public void record(long processingTimeNanos, @Nullable Throwable e) {
        this.successDuration.record(processingTimeNanos / 1_000_000d);
        this.successDuration.close();
    }
}
