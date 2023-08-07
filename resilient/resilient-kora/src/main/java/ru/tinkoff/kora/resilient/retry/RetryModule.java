package ru.tinkoff.kora.resilient.retry;

import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import javax.annotation.Nullable;

public interface RetryModule {

    default RetryConfig koraRetryableConfig(Config config, ConfigValueExtractor<RetryConfig> extractor) {
        var value = config.get("resilient");
        return extractor.extract(value);
    }

    default RetryManager koraRetryableManager(All<RetryPredicate> failurePredicates,
                                              RetryConfig config,
                                              @Nullable RetryMetrics metrics) {
        return new KoraRetryManager(config, failurePredicates,
            metrics == null
                ? new NoopRetryMetrics()
                : metrics);
    }

    default RetryPredicate koraRetrierFailurePredicate() {
        return new KoraRetryPredicate();
    }
}
