package io.koraframework.kafka.avro.telemetry.impl;

import io.koraframework.kafka.avro.telemetry.AvroRegistryTelemetry;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.TimeUnit;

/**
 * <b>Русский</b>: Реализация {@link AvroRegistryTelemetry} на основе Micrometer.
 * <hr>
 * <b>English</b>: Micrometer-backed implementation of {@link AvroRegistryTelemetry}.
 * <p>
 * Metrics:
 * <ul>
 *   <li>{@code kafka.avro.schema.registry.lookup} (timer) — tags: {@code operation}, {@code result}</li>
 *   <li>{@code kafka.avro.schema.cache} (counter) — tags: {@code operation}, {@code result}</li>
 *   <li>{@code kafka.avro.serde.error} (counter) — tags: {@code operation}, {@code topic}</li>
 * </ul>
 */
public final class DefaultAvroRegistryTelemetry implements AvroRegistryTelemetry {

    private static final String LOOKUP_METRIC = "kafka.avro.schema.registry.lookup";
    private static final String CACHE_METRIC = "kafka.avro.schema.cache";
    private static final String ERROR_METRIC = "kafka.avro.serde.error";

    private final MeterRegistry registry;

    public DefaultAvroRegistryTelemetry(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void observeRegistryLookup(String operation, boolean success, long durationNanos) {
        this.registry.timer(LOOKUP_METRIC, "operation", operation, "result", success ? "success" : "error")
            .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void observeCache(String operation, boolean hit) {
        this.registry.counter(CACHE_METRIC, "operation", operation, "result", hit ? "hit" : "miss")
            .increment();
    }

    @Override
    public void observeError(String operation, @Nullable String topic) {
        this.registry.counter(ERROR_METRIC, "operation", operation, "topic", topic == null ? "unknown" : topic)
            .increment();
    }
}
