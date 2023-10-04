package ru.tinkoff.kora.resilient.retry;

import jakarta.annotation.Nonnull;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class KoraRetryReactorBuilder {

    private static final Logger logger = LoggerFactory.getLogger(KoraRetryReactorBuilder.class);

    private final Map<String, Retry> retryableByName = new ConcurrentHashMap<>();
    private final List<RetryPredicate> failurePredicates;
    private final RetryConfig config;
    private final RetryMetrics metrics;

    public KoraRetryReactorBuilder(RetryConfig config, List<RetryPredicate> failurePredicates, RetryMetrics metrics) {
        this.config = config;
        this.failurePredicates = failurePredicates;
        this.metrics = metrics;
    }

    @Nonnull
    public reactor.util.retry.Retry get(@Nonnull String name) {
        return retryableByName.computeIfAbsent(name, (k) -> {
            final RetryConfig.NamedConfig config = this.config.getNamedConfig(name);
            final RetryPredicate failurePredicate = getFailurePredicate(config);
            logger.debug("Creating RetryReactor named '{}' with config {}", name, config);
            return new KoraReactorRetry(name, config, failurePredicate, metrics);
        });
    }

    private RetryPredicate getFailurePredicate(RetryConfig.NamedConfig config) {
        return failurePredicates.stream()
            .filter(p -> p.name().equals(config.failurePredicateName()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("FailurePredicateClassName " + config.failurePredicateName() + " is not present as bean, please declare it as bean"));
    }

    private static final class KoraReactorRetry extends reactor.util.retry.Retry {

        private static final Logger logger = LoggerFactory.getLogger(KoraReactorRetry.class);

        private final String name;
        private final long delayNanos;
        private final long delayStepNanos;
        private final int attempts;
        private final RetryPredicate failurePredicate;
        private final RetryMetrics metrics;

        private KoraReactorRetry(String name, long delayNanos, long delayStepNanos, int attempts, RetryPredicate failurePredicate, RetryMetrics metrics) {
            this.name = name;
            this.delayNanos = delayNanos;
            this.delayStepNanos = delayStepNanos;
            this.attempts = attempts;
            this.failurePredicate = failurePredicate;
            this.metrics = metrics;
        }

        private KoraReactorRetry(String name, RetryConfig.NamedConfig config, RetryPredicate failurePredicate, RetryMetrics metric) {
            this(name, config.delay().toNanos(), config.delayStep().toNanos(), config.attempts(), failurePredicate, metric);
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
                        logger.debug("RetryReactor '{}' exhausted all '{}' attempts", name, signal.totalRetries());
                        metrics.recordExhaustedAttempts(name, attempts);
                        final RetryExhaustedException exception = new RetryExhaustedException(attempts, currentFailure);
                        exception.addSuppressed(currentFailure);
                        return Mono.error(exception);
                    }

                    final long nextDelayNanos = delayNanos + (delayStepNanos * (signal.totalRetries() - 1));
                    final Duration delayDuration = Duration.ofNanos(nextDelayNanos);
                    logger.debug("RetryState '{}' initiating '{}' retry for '{}' due to exception: {}",
                        name, signal.totalRetries(), delayDuration, currentFailure.getClass().getCanonicalName());

                    metrics.recordAttempt(name, nextDelayNanos);
                    return Mono.delay(delayDuration);
                });
        }
    }
}
