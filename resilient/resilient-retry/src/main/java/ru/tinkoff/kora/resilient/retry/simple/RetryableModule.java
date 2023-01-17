package ru.tinkoff.kora.resilient.retry.simple;

import com.typesafe.config.Config;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.config.common.extractor.ObjectConfigValueExtractor;
import ru.tinkoff.kora.resilient.retry.RetrierFailurePredicate;
import ru.tinkoff.kora.resilient.retry.RetrierManager;
import ru.tinkoff.kora.resilient.retry.annotation.Retryable;
import ru.tinkoff.kora.resilient.retry.telemetry.RetryMetrics;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public interface RetryableModule {

    default ConfigValueExtractor<SimpleRetrierConfig> simpleRetryConfigValueExtractor(ConfigValueExtractor<Map<String, SimpleRetrierConfig.NamedConfig>> extractor) {
        return new ObjectConfigValueExtractor<>() {
            @Override
            protected SimpleRetrierConfig extract(Config config) {
                var map = Map.<String, SimpleRetrierConfig.NamedConfig>of();
                if (config.hasPath("retry")) {
                    map = extractor.extract(config.getValue("retry"));
                }
                return new SimpleRetrierConfig(map);
            }
        };
    }

    default SimpleRetrierConfig simpleRetryableConfig(Config config, ConfigValueExtractor<SimpleRetrierConfig> extractor) {
        return !config.hasPath("resilient")
            ? new SimpleRetrierConfig(Map.of())
            : extractor.extract(config.getValue("resilient"));
    }

    default RetrierManager simpleRetryableManager(@Tag(Retryable.class) ExecutorService executorService,
                                                  All<RetrierFailurePredicate> failurePredicates,
                                                  SimpleRetrierConfig config,
                                                  @Nullable RetryMetrics metrics) {
        return new SimpleRetrierManager(executorService, config, failurePredicates,
            metrics == null
                ? new NoopRetryMetrics()
                : metrics);
    }

    @DefaultComponent
    @Tag(Retryable.class)
    default ExecutorService simpleRetryableExecutorService() {
        return Executors.newCachedThreadPool();
    }

    default RetrierFailurePredicate simpleRetrierFailurePredicate() {
        return new SimpleRetrierFailurePredicate();
    }
}
