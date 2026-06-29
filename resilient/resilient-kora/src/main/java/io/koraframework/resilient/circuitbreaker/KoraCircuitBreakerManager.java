package io.koraframework.resilient.circuitbreaker;

import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class KoraCircuitBreakerManager implements CircuitBreakerManager {

    private static final Logger logger = LoggerFactory.getLogger(KoraCircuitBreakerManager.class);

    private final Map<String, CircuitBreaker> circuitBreakerMap = new ConcurrentHashMap<>();
    private final CircuitBreakerConfig config;
    private final Iterable<CircuitBreakerPredicate> failurePredicates;
    private final CircuitBreakerTelemetryFactory telemetryFactory;

    KoraCircuitBreakerManager(CircuitBreakerConfig config, Iterable<CircuitBreakerPredicate> failurePredicates, CircuitBreakerTelemetryFactory telemetryFactory) {
        this.config = config;
        this.failurePredicates = failurePredicates;
        this.telemetryFactory = telemetryFactory;
    }

    @Override
    public CircuitBreaker get(String name) {
        return circuitBreakerMap.computeIfAbsent(name, (k) -> {
            var config = this.config.getNamedConfig(name);
            final CircuitBreakerPredicate failurePredicate = getFailurePredicate(config);
            logger.atDebug()
                .addKeyValue("resilientType", "circuitbreaker")
                .addKeyValue("resilientName", name)
                .addKeyValue("failurePredicate", failurePredicate.name())
                .addKeyValue("config", config)
                .log("Creating CircuitBreaker");

            return new KoraCircuitBreaker(name, config, failurePredicate, this.telemetryFactory.get(name, this.config.telemetry()));
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
