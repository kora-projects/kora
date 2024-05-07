package ru.tinkoff.kora.micrometer.module.scheduling;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetrics;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public class MicrometerSchedulingMetricsFactory implements SchedulingMetricsFactory {
    private final MetricsConfig metricsConfig;
    private final MeterRegistry meterRegistry;

    public MicrometerSchedulingMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        this.metricsConfig = metricsConfig;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @Nullable
    public SchedulingMetrics get(TelemetryConfig.MetricsConfig config, Class<?> jobClass, String jobMethod) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return switch (this.metricsConfig.opentelemetrySpec()) {
                case V120 -> new Opentelemetry120SchedulingMetrics(this.meterRegistry, config, jobClass.getCanonicalName(), jobMethod);
                case V123 -> new Opentelemetry123SchedulingMetrics(this.meterRegistry, config, jobClass.getCanonicalName(), jobMethod);
            };
        } else {
            return null;
        }
    }
}
