package ru.tinkoff.kora.resilient.retry.simple;

import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.resilient.retry.RetrierFailurePredicate;
import ru.tinkoff.kora.resilient.retry.RetrierManager;
import ru.tinkoff.kora.resilient.retry.telemetry.RetryMetrics;

import javax.annotation.Nullable;

public interface RetryableModule {

    default SimpleRetrierConfig simpleRetryableConfig(Config config, ConfigValueExtractor<SimpleRetrierConfig> extractor) {
        var value = config.get("resilient");
        return extractor.extract(value);
    }

    default RetrierManager simpleRetryableManager(All<RetrierFailurePredicate> failurePredicates,
                                                  SimpleRetrierConfig config,
                                                  @Nullable RetryMetrics metrics) {
        return new SimpleRetrierManager(config, failurePredicates,
            metrics == null
                ? new NoopRetryMetrics()
                : metrics);
    }

    default RetrierFailurePredicate simpleRetrierFailurePredicate() {
        return new SimpleRetrierFailurePredicate();
    }
}
