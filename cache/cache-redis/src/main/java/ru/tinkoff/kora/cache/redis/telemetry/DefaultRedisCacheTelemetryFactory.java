package ru.tinkoff.kora.cache.redis.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.NOPLogger;
import ru.tinkoff.kora.cache.redis.RedisCacheClientConfig;
import ru.tinkoff.kora.cache.redis.RedisCacheConfig;

public class DefaultRedisCacheTelemetryFactory implements RedisCacheTelemetryFactory {
    @Nullable
    private final Tracer tracer;
    @Nullable
    private final MeterRegistry meterRegistry;

    public DefaultRedisCacheTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public RedisCacheTelemetry get(RedisCacheClientConfig clientConfig, RedisCacheConfig cacheConfig, String cacheName) {
        if (!cacheConfig.telemetry().tracing().enabled() && !cacheConfig.telemetry().logging().enabled() && !cacheConfig.telemetry().metrics().enabled()) {
            return NoopRedisTelemetry.INSTANCE;
        }
        var logger = cacheConfig.telemetry().logging().enabled()
            ? LoggerFactory.getLogger("ru.tinkoff.kora.cache.redis." + cacheName)
            : NOPLogger.NOP_LOGGER;
        var tracer = this.tracer == null || !cacheConfig.telemetry().tracing().enabled()
            ? TracerProvider.noop().get("redis-telemetry")
            : this.tracer;
        var meterRegistry = this.meterRegistry == null || cacheConfig.telemetry().metrics().enabled()
            ? new CompositeMeterRegistry()
            : this.meterRegistry;

        return new DefaultRedisCacheTelemetry(clientConfig, cacheConfig, cacheName, logger, tracer, meterRegistry);
    }
}
