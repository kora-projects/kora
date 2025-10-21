package ru.tinkoff.kora.micrometer.module.cache.redis.lettuce;

import io.lettuce.core.metrics.CommandLatencyRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.cache.redis.lettuce.telemetry.CommandLatencyRecorderFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class MicrometerLettuceCommandLatencyRecorderFactory implements CommandLatencyRecorderFactory {

    private final MeterRegistry registry;

    public MicrometerLettuceCommandLatencyRecorderFactory(MeterRegistry registry) {
        this.registry = registry;
    }

    @Nullable
    @Override
    public CommandLatencyRecorder get(String type, TelemetryConfig.MetricsConfig config) {
        if (!config.enabled()) {
            return null;
        }
        return new OpentelemetryLettuceCommandLatencyRecorder(type, this.registry, config);
    }
}
