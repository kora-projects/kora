package io.koraframework.resilient.bulkhead;

import io.koraframework.resilient.bulkhead.telemetry.BulkheadTelemetry;

import java.util.function.LongSupplier;

/**
 * Adaptive bulkhead using additive increase and multiplicative decrease (AIMD). The limit starts at
 * {@link BulkheadConfig.AdaptiveConfig#initialLimit()} and stays between
 * {@link BulkheadConfig.AdaptiveConfig#minLimit()} and {@link BulkheadConfig#maxConcurrentCalls()}.
 * <p>
 * Feedback is evaluated on permit completion after both the minimum sample count and sampling
 * interval are met. A window with any counted failure, or mean permit duration above
 * {@link BulkheadConfig.AdaptiveConfig#targetDuration()}, reduces the limit by
 * {@link BulkheadConfig.AdaptiveConfig#decreaseRatio()}, rounded down and clamped to the minimum. A
 * healthy window increases it by {@link BulkheadConfig.AdaptiveConfig#increaseStep()} only if
 * sampled admission reached the current limit. Low demand does not increase the budget.
 * <p>
 * Queue wait is excluded from duration feedback. Cancellation and errors marked non-circuitable or
 * non-retryable are excluded from sampling. Lowering the limit does not revoke existing permits;
 * new admission waits for enough operations to finish.
 * <p>
 * Use AIMD when available capacity changes and gradual exploration with quick backoff is
 * appropriate. Group operations with comparable durations and choose a meaningful duration target:
 * counted application failures also trigger backoff, even if concurrency was not their cause. The
 * configured queue policy works independently of adaptation.
 */
final class AimdKoraBulkhead extends AbstractAdaptiveKoraBulkhead {

    AimdKoraBulkhead(String name, BulkheadConfig config, BulkheadTelemetry telemetry, LongSupplier clock) {
        super(name, config, telemetry, clock);
        registerTelemetry();
    }

    @Override
    protected void onHealthyWindow(int samples, long elapsed) {
        increase();
    }
}
