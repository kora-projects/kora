package ru.tinkoff.kora.resilient.retry.simple;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import ru.tinkoff.kora.resilient.retry.RetrierFailurePredicate;
import ru.tinkoff.kora.resilient.retry.RetryAttemptException;
import ru.tinkoff.kora.resilient.retry.telemetry.RetryMetrics;

import java.time.Duration;

final class SimpleReactorRetry extends Retry {

    private static final Logger logger = LoggerFactory.getLogger(SimpleRetryState.class);

    private final String name;
    private final long delayNanos;
    private final long delayStepNanos;
    private final int attempts;
    private final RetrierFailurePredicate failurePredicate;
    private final RetryMetrics metrics;

    SimpleReactorRetry(String name, long delayNanos, long delayStepNanos, int attempts, RetrierFailurePredicate failurePredicate, RetryMetrics metrics) {
        this.name = name;
        this.delayNanos = delayNanos;
        this.delayStepNanos = delayStepNanos;
        this.attempts = attempts;
        this.failurePredicate = failurePredicate;
        this.metrics = metrics;
    }

    @Override
    public Publisher<?> generateCompanion(Flux<RetrySignal> retrySignals) {
        return retrySignals
            .concatMap(retryWhenState -> {
                //capture the state immediately
                final RetrySignal signal = retryWhenState.copy();
                final Throwable currentFailure = signal.failure();
                if (currentFailure == null) {
                    return Mono.error(new IllegalStateException("Retry.RetrySignal#failure() not expected to be null"));
                }

                if (!failurePredicate.test(currentFailure)) {
                    logger.trace("RetryReactor '{}' rejected throwable: {}", name, currentFailure.getClass().getCanonicalName());
                    return Mono.error(currentFailure);
                }

                if (signal.totalRetries() >= attempts) {
                    logger.trace("RetryReactor '{}' exhausted all '{}' attempts", name, signal.totalRetries());
                    metrics.recordExhaustedAttempts(name, attempts);
                    return Mono.error(new RetryAttemptException(attempts));
                }

                final long nextDelayNanos = delayNanos + (delayStepNanos * (signal.totalRetries() - 1));
                final Duration delayDuration = Duration.ofNanos(nextDelayNanos);
                logger.trace("RetryState '{}' initiating '{}' retry for '{}' due to throwable: {}",
                    name, signal.totalRetries(), delayDuration, currentFailure.getClass().getCanonicalName());

                metrics.recordAttempt(name, nextDelayNanos);
                return Mono.delay(delayDuration);
            });
    }
}
