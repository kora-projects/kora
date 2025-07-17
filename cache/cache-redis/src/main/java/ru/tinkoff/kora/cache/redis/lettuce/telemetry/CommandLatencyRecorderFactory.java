package ru.tinkoff.kora.cache.redis.lettuce.telemetry;

import io.lettuce.core.metrics.CommandLatencyRecorder;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface CommandLatencyRecorderFactory {

    @Nullable
    CommandLatencyRecorder get(String type, TelemetryConfig.MetricsConfig config);
}
