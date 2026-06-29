package io.koraframework.resilient.fallback;

import io.koraframework.resilient.fallback.telemetry.FallbackTelemetryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class KoraFallbackManager implements FallbackManager {

    private static final Logger logger = LoggerFactory.getLogger(KoraFallbackManager.class);

    private final Map<String, Fallback> fallbackerMap = new ConcurrentHashMap<>();

    private final FallbackConfig configs;
    private final FallbackTelemetryFactory telemetryFactory;
    private final Iterable<FallbackPredicate> failurePredicates;

    KoraFallbackManager(FallbackConfig configs, Iterable<FallbackPredicate> failurePredicates, FallbackTelemetryFactory telemetryFactory) {
        this.configs = configs;
        this.telemetryFactory = telemetryFactory;
        this.failurePredicates = failurePredicates;
    }

    @Override
    public Fallback get(String name) {
        return fallbackerMap.computeIfAbsent(name, k -> {
            final FallbackConfig.NamedConfig config = configs.getNamedConfig(name);
            final FallbackPredicate failurePredicate = getFailurePredicate(config);
            logger.atDebug()
                .addKeyValue("resilientType", "fallback")
                .addKeyValue("resilientName", name)
                .addKeyValue("failurePredicate", failurePredicate.name())
                .addKeyValue("config", config)
                .log("Creating Fallback");

            return new KoraFallback(name, this.telemetryFactory.get(name, this.configs.telemetry()), failurePredicate, config);
        });
    }

    private FallbackPredicate getFailurePredicate(FallbackConfig.NamedConfig config) {
        for (var p : failurePredicates) {
            if (p.name().equals(config.failurePredicateName())) {
                return p;
            }
        }
        throw new IllegalArgumentException("FailurePredicateClassName '" + config.failurePredicateName() + "' is not present as bean, please declare it as bean");
    }
}
