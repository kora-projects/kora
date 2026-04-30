package io.koraframework.cache.redis;

import io.koraframework.cache.redis.lettuce.LettuceCacheModule;
import io.koraframework.cache.redis.telemetry.DefaultRedisCacheMetricsFactory;
import io.koraframework.cache.redis.telemetry.DefaultRedisCacheTelemetryFactory;
import io.koraframework.cache.redis.telemetry.RedisCacheTelemetryFactory;
import io.koraframework.common.DefaultComponent;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface RedisCacheModule extends RedisCacheMapperModule, LettuceCacheModule {

    @DefaultComponent
    default RedisCacheTelemetryFactory redisCacheTelemetryFactory(@Nullable Tracer tracer,
                                                                  @Nullable MeterRegistry meterRegistry,
                                                                  @Nullable DefaultRedisCacheMetricsFactory metricsFactory) {
        return new DefaultRedisCacheTelemetryFactory(tracer, meterRegistry, metricsFactory);
    }
}
