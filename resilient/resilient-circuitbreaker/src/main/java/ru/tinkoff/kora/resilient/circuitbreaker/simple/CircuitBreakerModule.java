package ru.tinkoff.kora.resilient.circuitbreaker.simple;

import com.typesafe.config.Config;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.config.common.extractor.ObjectConfigValueExtractor;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerFailurePredicate;
import ru.tinkoff.kora.resilient.circuitbreaker.CircuitBreakerManager;
import ru.tinkoff.kora.resilient.circuitbreaker.telemetry.CircuitBreakerMetrics;

import javax.annotation.Nullable;
import java.util.Map;

public interface CircuitBreakerModule {
    default ConfigValueExtractor<SimpleCircuitBreakerConfig> fastCircuitBreakerConfigValueExtractor(ConfigValueExtractor<Map<String, SimpleCircuitBreakerConfig.NamedConfig>> extractor) {
        return new ObjectConfigValueExtractor<>() {
            @Override
            protected SimpleCircuitBreakerConfig extract(Config config) {
                var fast = Map.<String, SimpleCircuitBreakerConfig.NamedConfig>of();
                if (config.hasPath("circuitbreaker")) {
                    fast = extractor.extract(config.getValue("circuitbreaker"));
                }
                return new SimpleCircuitBreakerConfig(fast);
            }
        };
    }

    default SimpleCircuitBreakerConfig fastCircuitBreakerConfig(Config config, ConfigValueExtractor<SimpleCircuitBreakerConfig> extractor) {
        return !config.hasPath("resilient")
            ? new SimpleCircuitBreakerConfig(Map.of())
            : extractor.extract(config.getValue("resilient"));
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
