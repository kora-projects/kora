package io.koraframework.resilient.bulkhead;

import io.koraframework.resilient.bulkhead.telemetry.BulkheadTelemetry;

import java.util.function.LongSupplier;

/**
 * Bulkhead with a constant concurrency budget equal to {@link BulkheadConfig#maxConcurrentCalls()}.
 * Operation duration and failure feedback never change the limit.
 * <p>
 * A free slot is reserved until its permit is closed. At capacity, {@link #tryAcquire()} returns
 * {@code null}; {@link #acquire()} and {@link #acquireAsync()} either reject immediately or enter
 * the configured bounded FIFO queue. Queue capacity and wait timeout are separate from the number
 * of admitted operations.
 * <p>
 * Use this implementation when a dependency has a known safe concurrency budget or predictable
 * behaviour matters more than automatic tuning. It has no adaptive sampling overhead, but the
 * configured limit must be adjusted when the resource's capacity changes.
 */
final class FixedKoraBulkhead extends AbstractKoraBulkhead {

    FixedKoraBulkhead(String name, BulkheadConfig config, BulkheadTelemetry telemetry, LongSupplier clock) {
        super(name, config, telemetry, clock, config.maxConcurrentCalls());
        registerTelemetry();
    }

    @Override
    protected void sample(long now, long started, long duration, int concurrency, int admittedLimit, boolean failed) {}
}
