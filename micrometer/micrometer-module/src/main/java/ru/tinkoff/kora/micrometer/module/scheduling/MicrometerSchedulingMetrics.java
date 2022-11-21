package ru.tinkoff.kora.micrometer.module.scheduling;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetrics;

import javax.annotation.Nullable;

public class MicrometerSchedulingMetrics implements SchedulingMetrics {
    private final DistributionSummary successDuration;

    public MicrometerSchedulingMetrics(MeterRegistry meterRegistry, String className, String methodName) {
        this.successDuration = DistributionSummary.builder("scheduling.job.duration")
            .serviceLevelObjectives(1, 10, 50, 100, 200, 500, 1000, 2000, 5000, 10000, 20000, 30000, 60000, 90000)
            .baseUnit("milliseconds")
            .tag("code.function", methodName)
            .tag("code.class", className)
            .register(meterRegistry);
    }

    @Override
    public void record(long processingTimeNanos, @Nullable Throwable e) {
        this.successDuration.record(processingTimeNanos / 1_000_000d);
    }
}
