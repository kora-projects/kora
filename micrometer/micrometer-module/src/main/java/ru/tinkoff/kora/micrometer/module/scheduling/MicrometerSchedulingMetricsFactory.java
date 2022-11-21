package ru.tinkoff.kora.micrometer.module.scheduling;

import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetrics;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetricsFactory;

public class MicrometerSchedulingMetricsFactory implements SchedulingMetricsFactory {
    private final MeterRegistry meterRegistry;

    public MicrometerSchedulingMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public SchedulingMetrics get(Class<?> jobClass, String jobMethod) {
        return new MicrometerSchedulingMetrics(this.meterRegistry, jobClass.getCanonicalName(), jobMethod);
    }
}
