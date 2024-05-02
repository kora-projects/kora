package ru.tinkoff.kora.micrometer.module.camunda.zeebe.worker;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerMetrics;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class MicrometerZeebeWorkerMetricsFactory implements ZeebeWorkerMetricsFactory {

    private final MeterRegistry registry;

    public MicrometerZeebeWorkerMetricsFactory(MeterRegistry registry) {
        this.registry = registry;
    }

    @Nullable
    @Override
    public ZeebeWorkerMetrics get(TelemetryConfig.MetricsConfig config) {
        if (config.enabled() == null || Boolean.FALSE.equals(config.enabled())) {
            return null;
        }

        return new MicrometerZeebeWorkerMetrics(registry, config);
    }
}
