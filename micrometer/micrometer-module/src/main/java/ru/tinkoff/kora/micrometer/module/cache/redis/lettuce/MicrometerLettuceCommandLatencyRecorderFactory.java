package ru.tinkoff.kora.micrometer.module.cache.redis.lettuce;

import io.lettuce.core.metrics.CommandLatencyRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.cache.redis.lettuce.telemetry.CommandLatencyRecorderFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class MicrometerLettuceCommandLatencyRecorderFactory implements CommandLatencyRecorderFactory {

    private final MeterRegistry registry;
    private final MetricsConfig metricsConfig;

    public MicrometerLettuceCommandLatencyRecorderFactory(MeterRegistry registry, MetricsConfig metricsConfig) {
        this.registry = registry;
        this.metricsConfig = metricsConfig;
    }

    @Nullable
    @Override
    public CommandLatencyRecorder get(String type, TelemetryConfig.MetricsConfig config) {
        if (config.enabled() != null && !config.enabled()) {
            return null;
        }
        return switch (metricsConfig.opentelemetrySpec()) {
            case V120 -> new Opentelemetry120LettuceCommandLatencyRecorder(type, this.registry, config);
            case V123 -> new Opentelemetry123LettuceCommandLatencyRecorder(type, this.registry, config);
        };
    }
}
