package io.koraframework.resilient.retry;

import io.koraframework.resilient.retry.telemetry.RetryTelemetryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class KoraRetryManager implements RetryManager {

    private static final Logger logger = LoggerFactory.getLogger(KoraRetryManager.class);

    private final Map<String, Retry> retryableByName = new ConcurrentHashMap<>();
    private final Iterable<RetryPredicate> failurePredicates;
    private final RetryConfig config;
    private final RetryTelemetryFactory telemetryFactory;

    KoraRetryManager(RetryConfig config, Iterable<RetryPredicate> failurePredicates, RetryTelemetryFactory telemetryFactory) {
        this.config = config;
        this.failurePredicates = failurePredicates;
        this.telemetryFactory = telemetryFactory;
    }

    @Override
    public Retry get(String name) {
        return retryableByName.computeIfAbsent(name, (k) -> {
            final RetryConfig.NamedConfig config = this.config.getNamedConfig(name);
            final RetryPredicate failurePredicate = getFailurePredicate(config);
            logger.atDebug()
                .addKeyValue("resilientType", "retry")
                .addKeyValue("resilientName", name)
                .addKeyValue("config", config)
                .log("Creating Retry");
            return new KoraRetry(name, config, failurePredicate, this.telemetryFactory.get(name, this.config.telemetry()));
        });
    }

    private RetryPredicate getFailurePredicate(RetryConfig.NamedConfig config) {
        for (RetryPredicate p : failurePredicates) {
            if (p.name().equals(config.failurePredicateName())) {
                return p;
            }
        }
        throw new IllegalArgumentException("FailurePredicateClassName " + config.failurePredicateName() + " is not present as bean, please declare it as bean");
    }
}
