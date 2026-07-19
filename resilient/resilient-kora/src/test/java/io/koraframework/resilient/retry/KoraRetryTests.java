package io.koraframework.resilient.retry;

import io.koraframework.resilient.retry.exception.RetryExhaustedException;
import io.koraframework.resilient.retry.telemetry.RetryObservation;
import io.koraframework.resilient.retry.telemetry.RetryTelemetry;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class KoraRetryTests {

    private static final RuntimeException OPS = new IllegalStateException("Ops");

    @Test
    void legacyWithoutNewOptionsKeepsOldLinearBehavior() {
        var retry = retry(config(Duration.ofMillis(10), Duration.ofMillis(5), 2, null, null), null, new CountingTelemetry());
        var state = retry.asState();

        assertEquals(Retry.RetryState.RetryStatus.ACCEPTED, state.onException(OPS));
        assertEquals(Duration.ofMillis(10).toNanos(), state.getDelayNanos());
        assertEquals(Retry.RetryState.RetryStatus.ACCEPTED, state.onException(OPS));
        assertEquals(Duration.ofMillis(15).toNanos(), state.getDelayNanos());
    }

    @Test
    void fullJitterDelayIsWithinLinearRange() {
        var retry = retry(config(Duration.ofMillis(100), Duration.ofMillis(50), 2, jitter(), null), null, new CountingTelemetry());
        var computed = retry.computedDelayNanos(2);

        for (int i = 0; i < 100; i++) {
            var actual = retry.delayNanos(2);
            assertTrue(actual >= 0);
            assertTrue(actual <= computed);
        }
    }

    @Test
    void partialFullJitterDelayIsWithinRatioRange() {
        var retry = retry(config(Duration.ofMillis(100), Duration.ZERO, 2, jitter(0.25), null), null, new CountingTelemetry());
        var computed = retry.computedDelayNanos(1);

        for (int i = 0; i < 100; i++) {
            var actual = retry.delayNanos(1);
            assertTrue(actual >= Duration.ofMillis(75).toNanos());
            assertTrue(actual <= computed);
        }
    }

    @Test
    void noneJitterKeepsComputedDelay() {
        var retry = retry(config(Duration.ofMillis(100), Duration.ZERO, 2, new TestJitterConfig(RetryConfig.JitterType.NONE, 1.0), null), null, new CountingTelemetry());

        assertEquals(Duration.ofMillis(100).toNanos(), retry.delayNanos(1));
    }

    @Test
    void exponentialBackoffUsesMultiplierAndMaxDelay() {
        var retry = retry(config(Duration.ofMillis(100), Duration.ofMillis(50), 3, backoff(2.0, Duration.ofMillis(250)), null, null), null, new CountingTelemetry());

        assertEquals(Duration.ofMillis(100).toNanos(), retry.computedDelayNanos(1));
        assertEquals(Duration.ofMillis(200).toNanos(), retry.computedDelayNanos(2));
        assertEquals(Duration.ofMillis(250).toNanos(), retry.computedDelayNanos(3));
    }

    @Test
    void firstCallDoesNotRequireBudget() {
        var retryBudget = new KoraRetryBudget(0, 0, 0, 0);
        var retry = retry(config(Duration.ZERO, Duration.ZERO, 2, null, budget()), retryBudget, new CountingTelemetry());

        assertEquals("ok", retry.retry(() -> "ok"));
    }

    @Test
    void retryConsumesOneToken() {
        var retryBudget = new KoraRetryBudget(0, 1, 1, 0);
        var retry = retry(config(Duration.ZERO, Duration.ZERO, 2, null, budget()), retryBudget, new CountingTelemetry());
        var calls = new AtomicInteger();

        assertEquals("ok", retry.retry(() -> {
            if (calls.incrementAndGet() == 1) {
                throw OPS;
            }
            return "ok";
        }));

        assertEquals(0, retryBudget.availableTokens());
    }

    @Test
    void successAddsRatioToken() {
        var retryBudget = new KoraRetryBudget(0.5, 10, 0, 0);
        var retry = retry(config(Duration.ZERO, Duration.ZERO, 2, null, budget()), retryBudget, new CountingTelemetry());

        assertEquals("ok", retry.retry(() -> "ok"));

        assertEquals(0.5, retryBudget.availableTokens(), 0.000001);
    }

    @Test
    void budgetDoesNotExceedMaxTokens() {
        var retryBudget = new KoraRetryBudget(1, 2, 2, 0);
        var retry = retry(config(Duration.ZERO, Duration.ZERO, 2, null, budget()), retryBudget, new CountingTelemetry());

        retry.retry(() -> "ok");
        retry.retry(() -> "ok");

        assertEquals(2, retryBudget.availableTokens(), 0.000001);
    }

    @Test
    void budgetDeniedReturnsOriginalException() {
        var retryBudget = new KoraRetryBudget(0, 0, 0, 0);
        var telemetry = new CountingTelemetry();
        var retry = retry(config(Duration.ZERO, Duration.ZERO, 2, null, budget()), retryBudget, telemetry);
        var calls = new AtomicInteger();

        var exception = assertThrows(IllegalStateException.class, () -> retry.retry((Retry.RetrySupplier<String, RuntimeException>) () -> {
            calls.incrementAndGet();
            throw OPS;
        }));

        assertSame(OPS, exception);
        assertEquals(1, calls.get());
        assertEquals(RetryObservation.StopReason.EXHAUSTED_BUDGET, telemetry.stopReason.get());
    }

    @Test
    void attemptsExhaustedThrowsRetryExhaustedException() {
        var retryBudget = new KoraRetryBudget(0, 10, 10, 0);
        var telemetry = new CountingTelemetry();
        var retry = retry(config(Duration.ZERO, Duration.ZERO, 1, null, budget()), retryBudget, telemetry);

        assertThrows(RetryExhaustedException.class, () -> retry.retry((Retry.RetrySupplier<String, RuntimeException>) () -> {
            throw OPS;
        }));
        assertEquals(RetryObservation.StopReason.EXHAUSTED_ATTEMPTS, telemetry.stopReason.get());
    }

    @Test
    void nonRetryableFailureDoesNotConsumeBudget() {
        var retryBudget = new KoraRetryBudget(0, 1, 1, 0);
        var retry = new KoraRetry("test", config(Duration.ZERO, Duration.ZERO, 2, null, budget()), nonRetryablePredicate(), retryBudget, new CountingTelemetry());

        assertThrows(IllegalStateException.class, () -> retry.retry((Retry.RetrySupplier<String, RuntimeException>) () -> {
            throw OPS;
        }));
        assertEquals(1, retryBudget.availableTokens(), 0.000001);
    }

    @Test
    void asyncSuccessClosesTelemetry() {
        var telemetry = new CountingTelemetry();
        var retry = retry(config(Duration.ZERO, Duration.ZERO, 2, null, budget()), new KoraRetryBudget(0, 10, 10, 0), telemetry);

        assertEquals("ok", retry.retry(() -> CompletableFuture.completedFuture("ok")).toCompletableFuture().join());

        assertEquals(1, telemetry.observations.get());
        assertEquals(1, telemetry.ends.get());
    }

    @Test
    void asyncBudgetDeniedCompletesWithOriginalException() {
        var retry = retry(config(Duration.ZERO, Duration.ZERO, 2, null, budget()), new KoraRetryBudget(0, 0, 0, 0), new CountingTelemetry());

        var exception = assertThrows(CompletionException.class, () -> retry.retry(() -> CompletableFuture.failedFuture(OPS)).toCompletableFuture().join());

        assertSame(OPS, exception.getCause());
    }

    @Test
    void concurrentBudgetNeverGoesNegative() throws Exception {
        var retryBudget = new KoraRetryBudget(0, 10, 10, 0);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var successes = executor.invokeAll(java.util.stream.IntStream.range(0, 100)
                    .mapToObj(i -> (java.util.concurrent.Callable<Boolean>) retryBudget::tryAcquireRetryToken)
                    .toList())
                .stream()
                .filter(f -> {
                    try {
                        return f.get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .count();

            assertEquals(10, successes);
            assertEquals(0, retryBudget.availableTokens(), 0.000001);
        }
    }

    @Test
    void configValidationFailsFast() {
        var config = config(Duration.ZERO, Duration.ZERO, 1, null, budget(-1.0, 1, 1));

        assertThrows(IllegalArgumentException.class, () -> new KoraRetryBudget(config.retryBudget()));
    }

    @Test
    void configValidationRejectsInvalidJitterRatio() {
        var config = config(Duration.ZERO, Duration.ZERO, 1, new TestJitterConfig(RetryConfig.JitterType.FULL, 2.0), null);

        assertThrows(IllegalArgumentException.class, () -> retry(config, null, new CountingTelemetry()));
    }

    private static KoraRetry retry(RetryConfig config, KoraRetryBudget retryBudget, CountingTelemetry telemetry) {
        return new KoraRetry("test", config, predicate(), retryBudget, telemetry);
    }

    private static RetryPredicate predicate() {
        return new RetryPredicate() {
            @Override
            public boolean test(Throwable throwable) {
                return true;
            }
        };
    }

    private static RetryPredicate nonRetryablePredicate() {
        return new RetryPredicate() {
            @Override
            public boolean test(Throwable throwable) {
                return false;
            }
        };
    }

    private static RetryConfig config(Duration delay, Duration delayStep, int attempts, RetryConfig.JitterConfig jitter, RetryConfig.RetryBudgetConfig retryBudget) {
        return config(delay, delayStep, attempts, null, jitter, retryBudget);
    }

    private static RetryConfig config(Duration delay, Duration delayStep, int attempts, RetryConfig.BackoffConfig backoff, RetryConfig.JitterConfig jitter, RetryConfig.RetryBudgetConfig retryBudget) {
        return new TestRetryConfig(true, delay, delayStep, backoff, jitter, retryBudget, attempts, null);
    }

    private static RetryConfig.JitterConfig jitter() {
        return jitter(1.0);
    }

    private static RetryConfig.JitterConfig jitter(double ratio) {
        return new TestJitterConfig(RetryConfig.JitterType.FULL, ratio);
    }

    private static RetryConfig.BackoffConfig backoff(double multiplier, Duration delayMax) {
        return new TestBackoffConfig(RetryConfig.BackoffType.EXPONENTIAL, multiplier, delayMax);
    }

    private static RetryConfig.RetryBudgetConfig budget() {
        return new TestRetryBudgetConfig(true, 0.0, 1, 1, 0.0);
    }

    private static RetryConfig.RetryBudgetConfig budget(double ratio, int tokensMax, int tokensInitial) {
        return new TestRetryBudgetConfig(true, ratio, tokensMax, tokensInitial, 0.0);
    }

    private record TestRetryConfig(
        boolean enabled,
        Duration delay,
        Duration delayStep,
        RetryConfig.BackoffConfig backoff,
        RetryConfig.JitterConfig jitter,
        RetryConfig.RetryBudgetConfig retryBudget,
        int attempts,
        RetryConfig.TelemetryConfig telemetry
    ) implements RetryConfig {}

    private record TestJitterConfig(RetryConfig.JitterType type, double ratio) implements RetryConfig.JitterConfig {}

    private record TestBackoffConfig(RetryConfig.BackoffType type, double multiplier, Duration delayMax) implements RetryConfig.BackoffConfig {}

    private record TestRetryBudgetConfig(
        boolean enabled,
        double ratio,
        int tokensMax,
        int tokensInitial,
        double minTokensPerSecond
    ) implements RetryConfig.RetryBudgetConfig {}

    private static final class CountingTelemetry implements RetryTelemetry {

        private final AtomicInteger observations = new AtomicInteger();
        private final AtomicInteger ends = new AtomicInteger();
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicInteger exhausted = new AtomicInteger();
        private final AtomicReference<RetryObservation.StopReason> stopReason = new AtomicReference<>();

        @Override
        public RetryObservation observe() {
            observations.incrementAndGet();
            return new RetryObservation() {
                @Override
                public void recordAttempt(long delayInNanos) {
                    attempts.incrementAndGet();
                }

                @Override
                public void recordExhausted(StopReason reason, int totalAttempts) {
                    exhausted.incrementAndGet();
                    stopReason.set(reason);
                }

                @Override
                public Span span() {
                    return Span.getInvalid();
                }

                @Override
                public void end() {
                    ends.incrementAndGet();
                }

                @Override
                public void observeError(Throwable e) {}
            };
        }
    }
}
