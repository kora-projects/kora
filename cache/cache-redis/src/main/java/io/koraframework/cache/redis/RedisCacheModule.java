package io.koraframework.cache.redis;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import io.koraframework.cache.redis.lettuce.LettuceModule;
import io.koraframework.cache.redis.telemetry.DefaultRedisCacheTelemetryFactory;
import io.koraframework.cache.redis.telemetry.RedisCacheTelemetryFactory;

public interface RedisCacheModule extends RedisCacheMapperModule, LettuceModule {
    default RedisCacheTelemetryFactory redisCacheTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        return new DefaultRedisCacheTelemetryFactory(tracer, meterRegistry);
    }
}
