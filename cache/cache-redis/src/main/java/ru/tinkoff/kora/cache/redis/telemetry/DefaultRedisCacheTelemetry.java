package ru.tinkoff.kora.cache.redis.telemetry;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.noop.NoopCounter;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import ru.tinkoff.kora.cache.redis.RedisCacheClientConfig;
import ru.tinkoff.kora.cache.redis.RedisCacheConfig;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultRedisCacheTelemetry implements RedisCacheTelemetry {
    private static final String TAG_OPERATION = "operation";
    private static final String TAG_CACHE_NAME = "cache";
    private static final String TAG_ORIGIN = "origin";
    private static final Timer NOOP_DURATION = new NoopTimer(new Meter.Id("noop", Tags.empty(), null, null, Meter.Type.TIMER));
    private static final Counter NOOP_COUNTER = new NoopCounter(new Meter.Id("noop", Tags.empty(), null, null, Meter.Type.COUNTER));


    protected RedisCacheClientConfig clientConfig;
    protected RedisCacheConfig cacheConfig;
    protected String cacheName;
    protected Logger logger;
    protected Tracer tracer;
    protected MeterRegistry meterRegistry;
    private final ConcurrentMap<String, ConcurrentMap<Tags, Timer>> durations = new ConcurrentHashMap<>();
    private final ConcurrentMap<Tags, Counter> ratios = new ConcurrentHashMap<>();

    public DefaultRedisCacheTelemetry(RedisCacheClientConfig clientConfig, RedisCacheConfig cacheConfig, String cacheName, Logger logger, Tracer tracer, MeterRegistry meterRegistry) {
        this.clientConfig = clientConfig;
        this.cacheConfig = cacheConfig;
        this.cacheName = cacheName;
        this.logger = logger;
        this.tracer = tracer;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public RedisCacheObservation observe(String operation) {
        var span = this.createSpan(operation);
        var duration = this.duration(operation);
        var ratio = this.ratio(operation);

        return new DefaultRedisCacheObservation(operation, this.cacheName, span, duration, ratio, logger);
    }

    protected Meter.MeterProvider<Timer> duration(String operation) {
        if (!this.cacheConfig.telemetry().metrics().enabled()) {
            return _ -> NOOP_DURATION;
        }
        var operationDurations = this.durations.computeIfAbsent(operation, k -> new ConcurrentHashMap<>());

        return t -> operationDurations.computeIfAbsent(Tags.of(t), tags -> {
            var builder = Timer.builder("cache.duration")
                .tag(TAG_CACHE_NAME, this.cacheName)
                .tag(TAG_OPERATION, operation)
                .tag(TAG_ORIGIN, "redis")
                .tags(tags);

            return builder.register(meterRegistry);
        });
    }

    protected Meter.MeterProvider<Counter> ratio(String operation) {
        if (!this.cacheConfig.telemetry().metrics().enabled() || !"GET".equals(operation)) {
            return _ -> NOOP_COUNTER;
        }

        return t -> ratios.computeIfAbsent(Tags.of(t), tags -> {
            var builder = Counter.builder("cache.ratio")
                .tag(TAG_CACHE_NAME, this.cacheName)
                .tag(TAG_ORIGIN, "redis")
                .tags(tags);

            return builder.register(meterRegistry);
        });
    }

    protected Span createSpan(String operation) {
        if (!cacheConfig.telemetry().tracing().enabled()) {
            return Span.getInvalid();
        }
        var span = this.tracer.spanBuilder("cache.call")
            .setSpanKind(SpanKind.INTERNAL)
            .setAttribute(TAG_OPERATION, operation)
            .setAttribute(TAG_CACHE_NAME, this.cacheName)
            .setAttribute(TAG_ORIGIN, "redis");

        for (var e : cacheConfig.telemetry().tracing().attributes().entrySet()) {
            span.setAttribute(e.getKey(), e.getValue());
        }

        return span.startSpan();
    }
}
