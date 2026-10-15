package io.koraframework.resilient.bulkhead;

import io.koraframework.resilient.bulkhead.telemetry.BulkheadTelemetry;

import java.util.function.LongSupplier;

/**
 * Adaptive bulkhead that probes whether additional concurrency improves completion throughput. It
 * shares AIMD's initial/minimum/maximum limits and multiplicative backoff on counted failures or
 * excessive mean permit duration.
 * <p>
 * Each healthy window whose sampled admission reached the current limit measures throughput as
 * sampled completions divided by elapsed window time. The first window stores a baseline and
 * increases the limit by {@link BulkheadConfig.AdaptiveConfig#increaseStep()}. A later window
 * retains the probe and starts another only when throughput exceeds the baseline by
 * {@link BulkheadConfig.AdaptiveConfig#throughputTolerance()}. Otherwise the previous baseline
 * limit is restored and the baseline is cleared, allowing a later probe. Congestion or an
 * unsaturated window also clears the baseline.
 * <p>
 * Queue wait is excluded from duration feedback, and cancellation and errors marked non-circuitable
 * or non-retryable are excluded from sampling. Existing permits are not revoked when the limit
 * decreases. Immediate rejection and bounded FIFO admission use the same configuration as the other
 * implementations.
 * <p>
 * Use this strategy under sustained demand when the goal is to avoid adding concurrency after
 * completion throughput plateaus. Comparisons work best with a stable mix of operations; traffic
 * changes and short noisy windows can distort probe results. This is a window-based heuristic, not
 * a guarantee of optimal throughput or a per-operation latency bound.
 */
final class ThroughputKoraBulkhead extends AbstractAdaptiveKoraBulkhead {

    private double baselineThroughput = Double.NaN;
    private int baselineLimit;

    ThroughputKoraBulkhead(String name, BulkheadConfig config, BulkheadTelemetry telemetry, LongSupplier clock) {
        super(name, config, telemetry, clock);
        registerTelemetry();
    }

    @Override
    protected void onHealthyWindow(int samples, long elapsed) {
        double throughput = (double) samples * 1_000_000_000 / elapsed;
        if (Double.isNaN(baselineThroughput) || throughput > baselineThroughput * (1 + adaptive.throughputTolerance())) {
            baselineThroughput = throughput;
            baselineLimit = currentLimit;
            increase();
        } else {
            currentLimit = baselineLimit;
            resetFeedback();
        }
    }

    @Override
    protected void resetFeedback() {
        baselineThroughput = Double.NaN;
    }
}
