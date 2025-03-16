package ru.tinkoff.kora.micrometer.module.cache;

import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.cache.telemetry.CacheMetrics;
import ru.tinkoff.kora.cache.telemetry.CacheMetricsFactory;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetryArgs;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class MicrometerCacheMetricsFactory implements CacheMetricsFactory {

    private final MeterRegistry meterRegistry;

    public MicrometerCacheMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public CacheMetrics get(TelemetryConfig.MetricsConfig config, CacheTelemetryArgs args) {
        if (Objects.requireNonNullElse(config.enabled(), true)) {
            return new Opentelemetry120CacheMetrics(meterRegistry, config);
        } else {
            return null;
        }
    }
}
