package ru.tinkoff.kora.micrometer.module.cache.redis.lettuce;

import io.lettuce.core.metrics.CommandLatencyRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import ru.tinkoff.kora.cache.redis.lettuce.telemetry.CommandLatencyRecorderFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public final class MicrometerLettuceCommandLatencyRecorderFactory implements CommandLatencyRecorderFactory {

    private final MeterRegistry registry;

    public MicrometerLettuceCommandLatencyRecorderFactory(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public CommandLatencyRecorder get(String type, TelemetryConfig.MetricsConfig config) {
        if (!config.enabled()) {
            return CommandLatencyRecorder.disabled();
        }
        return new OpentelemetryLettuceCommandLatencyRecorder(type, this.registry, config);
    }
}
