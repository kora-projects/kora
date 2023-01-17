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

final class FastCircuitBreakerManager implements CircuitBreakerManager {

    private static final Logger logger = LoggerFactory.getLogger(FastCircuitBreakerManager.class);

    private final Map<String, CircuitBreaker> circuitBreakerMap = new ConcurrentHashMap<>();
    private final FastCircuitBreakerConfig config;
    private final List<CircuitBreakerFailurePredicate> failurePredicates;
    private final CircuitBreakerMetrics metrics;

    FastCircuitBreakerManager(FastCircuitBreakerConfig config, List<CircuitBreakerFailurePredicate> failurePredicates, CircuitBreakerMetrics metrics) {
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

            return new FastCircuitBreaker(name, config, failurePredicate, metrics);
        });
    }

    private CircuitBreakerFailurePredicate getFailurePredicate(FastCircuitBreakerConfig.NamedConfig config) {
        return failurePredicates.stream()
            .filter(p -> p.name().equals(config.failurePredicateName()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("FailurePredicateClassName " + config.failurePredicateName() + " is not present as bean, please declare it as bean"));
    }
}
