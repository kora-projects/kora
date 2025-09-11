package ru.tinkoff.kora.micrometer.module.camunda.zeebe.worker;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerMetrics;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class MicrometerZeebeWorkerMetricsFactory implements ZeebeWorkerMetricsFactory {

    private final MeterRegistry meterRegistry;

    public MicrometerZeebeWorkerMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Nullable
    @Override
    public ZeebeWorkerMetrics get(TelemetryConfig.MetricsConfig config) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new MicrometerZeebeWorkerMetrics(this.meterRegistry, config);
        } else {
            return null;
        }
    }
}
