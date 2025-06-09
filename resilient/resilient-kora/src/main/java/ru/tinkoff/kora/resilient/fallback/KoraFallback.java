package ru.tinkoff.kora.resilient.fallback;

import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

final class KoraFallback implements Fallback {

    private static final Logger logger = LoggerFactory.getLogger(KoraFallback.class);

    private final String name;
    private final FallbackMetrics metrics;
    private final FallbackPredicate failurePredicate;
    private final FallbackConfig.NamedConfig config;

    KoraFallback(String name, FallbackMetrics metrics, FallbackPredicate failurePredicate, FallbackConfig.NamedConfig config) {
        this.name = name;
        this.metrics = metrics;
        this.failurePredicate = failurePredicate;
        this.config = config;
    }

    @Override
    public boolean canFallback(Throwable throwable) {
        if (!config.enabled()) {
            logger.debug("Fallback '{}' is disabled", name);
            return false;
        } else if (failurePredicate.test(throwable)) {
            logger.debug("Initiating Fallback '{}' due to: {}", name, throwable.toString());
            metrics.recordExecute(name, throwable);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void fallback(@Nonnull Runnable runnable, @Nonnull Runnable fallback) {
        if (!config.enabled()) {
            logger.debug("Fallback '{}' is disabled", name);
            runnable.run();
            return;
        }

        try {
            runnable.run();
        } catch (Throwable e) {
            if (canFallback(e)) {
                fallback.run();
            } else {
                throw e;
            }
        }
    }

    @Override
    public <T> T fallback(@Nonnull Supplier<T> supplier, @Nonnull Supplier<T> fallback) {
        if (!config.enabled()) {
            logger.debug("Fallback '{}' is disabled", name);
            return supplier.get();
        }

        try {
            return supplier.get();
        } catch (Throwable e) {
            if (canFallback(e)) {
                return fallback.get();
            } else {
                throw e;
            }
        }
    }
}
