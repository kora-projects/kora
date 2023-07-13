package ru.tinkoff.kora.micrometer.module.scheduling;

import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.micrometer.module.MetricsConfig.SchedulingMetricsConfig;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetrics;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetricsFactory;

public class MicrometerSchedulingMetricsFactory implements SchedulingMetricsFactory {
    private final MeterRegistry meterRegistry;
    private final SchedulingMetricsConfig config;

    public MicrometerSchedulingMetricsFactory(MeterRegistry meterRegistry, SchedulingMetricsConfig config) {
        this.meterRegistry = meterRegistry;
        this.config = config;
    }

    @Override
    public SchedulingMetrics get(Class<?> jobClass, String jobMethod) {
        return new MicrometerSchedulingMetrics(this.meterRegistry, this.config, jobClass.getCanonicalName(), jobMethod);
    }
}
