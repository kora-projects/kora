package io.koraframework.resilient.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class KoraCircuitBreakerManager implements CircuitBreakerManager {

    private static final Logger logger = LoggerFactory.getLogger(KoraCircuitBreakerManager.class);

    private final Map<String, CircuitBreaker> circuitBreakerMap = new ConcurrentHashMap<>();
    private final CircuitBreakerConfig config;
    private final Iterable<CircuitBreakerPredicate> failurePredicates;
    private final CircuitBreakerMetrics metrics;

    KoraCircuitBreakerManager(CircuitBreakerConfig config, Iterable<CircuitBreakerPredicate> failurePredicates, CircuitBreakerMetrics metrics) {
        this.config = config;
        this.failurePredicates = failurePredicates;
        this.metrics = metrics;
    }

    @Override
    public CircuitBreaker get(String name) {
        return circuitBreakerMap.computeIfAbsent(name, (k) -> {
            var config = this.config.getNamedConfig(name);
            final CircuitBreakerPredicate failurePredicate = getFailurePredicate(config);
            logger.debug("Creating CircuitBreaker named '{}' with failure predicate '{}' and config {}",
                name, failurePredicate.name(), config);

            return new KoraCircuitBreaker(name, config, failurePredicate, metrics);
        });
    }

    private CircuitBreakerPredicate getFailurePredicate(CircuitBreakerConfig.NamedConfig config) {
        for (var p : failurePredicates) {
            if (p.name().equals(config.failurePredicateName())) {
                return p;
            }
        }
        throw new IllegalArgumentException("FailurePredicateClassName " + config.failurePredicateName() + " is not present as bean, please declare it as bean");
    }
}
