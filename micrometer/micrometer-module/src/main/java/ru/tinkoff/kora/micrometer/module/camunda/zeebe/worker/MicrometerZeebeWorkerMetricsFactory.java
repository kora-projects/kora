package ru.tinkoff.kora.micrometer.module.camunda.zeebe.worker;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerMetrics;
import ru.tinkoff.kora.camunda.zeebe.worker.telemetry.ZeebeWorkerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class MicrometerZeebeWorkerMetricsFactory implements ZeebeWorkerMetricsFactory {

    private final MeterRegistry meterRegistry;
    private final MetricsConfig metricsConfig;

    public MicrometerZeebeWorkerMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        this.meterRegistry = meterRegistry;
        this.metricsConfig = metricsConfig;
    }

    @Nullable
    @Override
    public ZeebeWorkerMetrics get(TelemetryConfig.MetricsConfig config) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return switch (metricsConfig.opentelemetrySpec()) {
                case V120 -> new Micrometer120ZeebeWorkerMetrics(this.meterRegistry, config);
                case V123 -> new Micrometer123ZeebeWorkerMetrics(this.meterRegistry, config);
            };
        } else {
            return null;
        }
    }
}
