package io.koraframework.resilient.bulkhead;

import io.koraframework.resilient.bulkhead.telemetry.BulkheadTelemetry;

import java.util.function.LongSupplier;

/**
 * Shared completion sampling and congestion feedback for adaptive bulkheads, serialized by the
 * admission lock. Concrete strategies decide how a healthy saturated window changes the limit; this
 * base supplies initial/minimum bounds and congestion backoff.
 * <p>
 * Only completions whose admission limit equals the current limit are sampled. A window tracks
 * completion count, failures, mean permit duration and the highest concurrency recorded at
 * admission. Evaluation requires both the configured sample count and elapsed interval. Any counted
 * failure or excessive mean duration reduces the limit multiplicatively; healthy windows reach the
 * strategy only if sampled admission reached the limit.
 * <p>
 * Queue time and ignored completions are excluded by the shared admission implementation.
 * Reductions affect future admission and do not cancel operations already in flight.
 */
abstract class AbstractAdaptiveKoraBulkhead extends AbstractKoraBulkhead {

    protected final BulkheadConfig.AdaptiveConfig adaptive;
    private final long interval;
    private final long target;
    private long windowStart;
    private double totalDuration;
    private int samples;
    private int failures;
    private int peak;

    AbstractAdaptiveKoraBulkhead(String name, BulkheadConfig config, BulkheadTelemetry telemetry, LongSupplier clock) {
        this(name, config, telemetry, clock, config.adaptive() == null ? new BulkheadConfig.AdaptiveConfig() {} : config.adaptive());
    }

    private AbstractAdaptiveKoraBulkhead(
        String name,
        BulkheadConfig config,
        BulkheadTelemetry telemetry,
        LongSupplier clock,
        BulkheadConfig.AdaptiveConfig adaptive
    ) {
        super(name, config, telemetry, clock, adaptive.initialLimit());
        this.adaptive = adaptive;
        this.interval = adaptive.samplingInterval().toNanos();
        this.target = adaptive.targetDuration().toNanos();
    }

    @Override
    protected final void sample(long now, long started, long duration, int concurrency, int admittedLimit, boolean failed) {
        // A completion admitted before a limit change must not drive the next probe.
        if (admittedLimit != currentLimit) {
            return;
        }
        if (samples == 0) {
            windowStart = started;
        }
        samples++;
        totalDuration += duration;
        failures += failed ? 1 : 0;
        peak = Math.max(peak, concurrency);
        long elapsed = now - windowStart;
        if (samples < adaptive.minimumSamples() || elapsed < interval) {
            return;
        }

        if (failures > 0 || totalDuration / samples > target) {
            currentLimit = Math.max(adaptive.minLimit(), (int) Math.floor(currentLimit * adaptive.decreaseRatio()));
            resetFeedback();
        } else if (peak >= currentLimit) {
            onHealthyWindow(samples, elapsed);
        } else {
            // Low offered load is not evidence that more capacity would improve throughput.
            resetFeedback();
        }
        samples = failures = peak = 0;
        totalDuration = 0;
    }

    protected abstract void onHealthyWindow(int samples, long elapsed);

    protected void resetFeedback() {}

    protected final void increase() {
        currentLimit = (int) Math.min(maxConcurrentCalls(), (long) currentLimit + adaptive.increaseStep());
    }
}
