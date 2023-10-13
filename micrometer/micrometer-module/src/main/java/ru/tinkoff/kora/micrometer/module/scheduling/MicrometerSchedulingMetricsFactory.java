package ru.tinkoff.kora.micrometer.module.scheduling;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetrics;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public class MicrometerSchedulingMetricsFactory implements SchedulingMetricsFactory {
    private final MeterRegistry meterRegistry;

    public MicrometerSchedulingMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Nullable
    public SchedulingMetrics get(TelemetryConfig.MetricsConfig config, Class<?> jobClass, String jobMethod) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new MicrometerSchedulingMetrics(this.meterRegistry, config, jobClass.getCanonicalName(), jobMethod);
        } else {
            return null;
        }
    }
}
