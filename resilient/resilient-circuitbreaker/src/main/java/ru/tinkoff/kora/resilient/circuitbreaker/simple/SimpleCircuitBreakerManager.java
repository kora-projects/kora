package ru.tinkoff.kora.resilient.circuitbreaker.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreaker;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerFailurePredicate;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerManager;
import ru.tinkoff.kora.resilient.circuitbreaker.telemetry.CircuitBreakerMetrics;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class SimpleCircuitBreakerManager implements CircuitBreakerManager {

    private static final Logger logger = LoggerFactory.getLogger(SimpleCircuitBreakerManager.class);

    private final Map<String, CircuitBreaker> circuitBreakerMap = new ConcurrentHashMap<>();
    private final SimpleCircuitBreakerConfig config;
    private final List<CircuitBreakerFailurePredicate> failurePredicates;
    private final CircuitBreakerMetrics metrics;

    SimpleCircuitBreakerManager(SimpleCircuitBreakerConfig config, List<CircuitBreakerFailurePredicate> failurePredicates, CircuitBreakerMetrics metrics) {
        this.config = config;
        this.failurePredicates = failurePredicates;
        this.metrics = metrics;
    }

    @Nonnull
    @Override
    public CircuitBreaker get(@Nonnull String name) {
        return circuitBreakerMap.computeIfAbsent(name, (k) -> {
            var config = this.config.getNamedConfig(name);
            final CircuitBreakerFailurePredicate failurePredicate = getFailurePredicate(config);
            logger.debug("Creating CircuitBreaker named '{}' with failure predicate '{}' and config {}",
                name, failurePredicate.name(), config);

            return new SimpleCircuitBreaker(name, config, failurePredicate, metrics);
        });
    }

    private CircuitBreakerFailurePredicate getFailurePredicate(SimpleCircuitBreakerConfig.NamedConfig config) {
        return failurePredicates.stream()
            .filter(p -> p.name().equals(config.failurePredicateName()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("FailurePredicateClassName " + config.failurePredicateName() + " is not present as bean, please declare it as bean"));
    }
}
