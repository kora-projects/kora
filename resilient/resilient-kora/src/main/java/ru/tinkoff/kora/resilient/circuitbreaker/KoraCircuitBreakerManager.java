package ru.tinkoff.kora.resilient.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class KoraCircuitBreakerManager implements CircuitBreakerManager {

    private static final Logger logger = LoggerFactory.getLogger(KoraCircuitBreakerManager.class);

    private final Map<String, CircuitBreaker> circuitBreakerMap = new ConcurrentHashMap<>();
    private final CircuitBreakerConfig config;
    private final List<CircuitBreakerPredicate> failurePredicates;
    private final CircuitBreakerMetrics metrics;

    KoraCircuitBreakerManager(CircuitBreakerConfig config, List<CircuitBreakerPredicate> failurePredicates, CircuitBreakerMetrics metrics) {
        this.config = config;
        this.failurePredicates = failurePredicates;
        this.metrics = metrics;
    }

    @Nonnull
    @Override
    public CircuitBreaker get(@Nonnull String name) {
        return circuitBreakerMap.computeIfAbsent(name, (k) -> {
            var config = this.config.getNamedConfig(name);
            final CircuitBreakerPredicate failurePredicate = getFailurePredicate(config);
            logger.debug("Creating CircuitBreaker named '{}' with failure predicate '{}' and config {}",
                name, failurePredicate.name(), config);

            return new KoraCircuitBreaker(name, config, failurePredicate, metrics);
        });
    }

    private CircuitBreakerPredicate getFailurePredicate(CircuitBreakerConfig.NamedConfig config) {
        return failurePredicates.stream()
            .filter(p -> p.name().equals(config.failurePredicateName()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("FailurePredicateClassName " + config.failurePredicateName() + " is not present as bean, please declare it as bean"));
    }
}
