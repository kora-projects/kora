package io.koraframework.resilient.circuitbreaker;

import io.koraframework.application.graph.All;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CircuitBreakerFactoryModule {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerFactoryModule.class);

    private final String configPath;

    public CircuitBreakerFactoryModule(String configPath) {
        this.configPath = configPath;
    }

    @Tag(Tag.Factory.class)
    public CircuitBreakerConfig config(Config config, ConfigValueMapper<CircuitBreakerConfig> mapper) {
        var circuitBreakerConfig = mapper.mapOrThrow(config.get(this.configPath));
        validate(this.configPath, circuitBreakerConfig);
        return circuitBreakerConfig;
    }

    @Tag(Tag.Factory.class)
    public CircuitBreaker circuitBreaker(@Tag(Tag.Factory.class) CircuitBreakerConfig config,
                                         All<CircuitBreakerPredicate> failurePredicates,
                                         CircuitBreakerTelemetryFactory telemetryFactory) {
        final CircuitBreakerPredicate failurePredicate = getFailurePredicate(config.failurePredicateName(), failurePredicates);
        logger.atDebug()
            .addKeyValue("resilientType", "circuitbreaker")
            .addKeyValue("resilientConfig", this.configPath)
            .addKeyValue("failurePredicate", failurePredicate.name())
            .addKeyValue("config", config)
            .log("Creating CircuitBreaker");

        var telemetry = telemetryFactory.get(this.configPath, config.telemetry());
        return new KoraCircuitBreaker(this.configPath, config, failurePredicate, telemetry);
    }

    private CircuitBreakerPredicate getFailurePredicate(String name, Iterable<CircuitBreakerPredicate> failurePredicates) {
        for (var p : failurePredicates) {
            if (name.equals(p.name())) {
                return p;
            }
        }
        throw new IllegalArgumentException("FailurePredicateClassName " + name + " is not present as bean, please declare it as bean");
    }

    private static void validate(String configPath, CircuitBreakerConfig config) {
        if (config.minimumRequiredCalls() < 1) {
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(configPath, "minimumRequiredCalls", config.minimumRequiredCalls()));
        }
        if (config.slidingWindowSize() < 1) {
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(configPath, "slidingWindowSize", config.slidingWindowSize()));
        }
        if (config.permittedCallsInHalfOpenState() < 1) {
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' can't be negative or zero value, but was: %s"
                .formatted(configPath, "permittedCallsInHalfOpenState", config.permittedCallsInHalfOpenState()));
        }
        if (config.minimumRequiredCalls() > config.slidingWindowSize()) {
            throw new IllegalArgumentException("CircuitBreaker '%s' property '%s' has value %s, it can't be greater than property '%s' which value was: %s"
                .formatted(configPath, "minimumRequiredCalls", config.minimumRequiredCalls(), "slidingWindowSize", config.slidingWindowSize()));
        }
        if (config.failureRateThreshold() > 100 || config.failureRateThreshold() < 1) {
            throw new IllegalArgumentException("CircuitBreaker '%s' failureRateThreshold is percentage and must be in range from 1 to 100, but was: %s"
                .formatted(configPath, config.failureRateThreshold()));
        }
    }
}
