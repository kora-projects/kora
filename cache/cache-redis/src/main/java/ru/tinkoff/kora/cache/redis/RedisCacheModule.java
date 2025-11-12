package ru.tinkoff.kora.cache.redis;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.cache.redis.lettuce.LettuceModule;
import ru.tinkoff.kora.cache.redis.telemetry.DefaultRedisCacheTelemetryFactory;
import ru.tinkoff.kora.cache.redis.telemetry.RedisCacheTelemetryFactory;

public interface RedisCacheModule extends RedisCacheMapperModule, LettuceModule {
    default RedisCacheTelemetryFactory redisCacheTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        return new DefaultRedisCacheTelemetryFactory(tracer, meterRegistry);
    }
}
