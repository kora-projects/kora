package ru.tinkoff.kora.resilient.circuitbreaker.simple;

import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.ConfigValue;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerFailurePredicate;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerManager;
import ru.tinkoff.kora.resilient.circuitbreaker.telemetry.CircuitBreakerMetrics;

import javax.annotation.Nullable;

public interface CircuitBreakerModule {
    default SimpleCircuitBreakerConfig fastCircuitBreakerConfig(Config config, ConfigValueExtractor<SimpleCircuitBreakerConfig> extractor) {
        var resilient = config.get("resilient");
        return extractor.extract(resilient);
    }

    default CircuitBreakerManager fastCircuitBreakerManager(SimpleCircuitBreakerConfig config,
                                                            All<CircuitBreakerFailurePredicate> failurePredicates,
                                                            @Nullable CircuitBreakerMetrics metrics) {
        return new SimpleCircuitBreakerManager(config, failurePredicates,
            (metrics == null)
                ? new NoopCircuitBreakerMetrics()
                : metrics);
    }

    default CircuitBreakerFailurePredicate fastDefaultCircuitBreakerFailurePredicate() {
        return new SimpleCircuitBreakerFailurePredicate();
    }
}
