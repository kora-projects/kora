package ru.tinkoff.kora.resilient.fallback;

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
        if (Boolean.FALSE.equals(config.enabled())) {
            logger.debug("Fallback '{}' is disabled", name);
            return false;
        } else if (failurePredicate.test(throwable)) {
            if (logger.isTraceEnabled()) {
                logger.trace("Fallback '{}' initiating due to exception", name, throwable);
            } else if (logger.isDebugEnabled()) {
                logger.debug("Fallback '{}' initiating due to exception: {}", name, throwable.toString());
            }
            metrics.recordExecute(name, throwable);
            return true;
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("Fallback '{}' rejected exception due to predicate", name, throwable);
            } else if (logger.isDebugEnabled()) {
                logger.debug("Fallback '{}' rejected exception due to predicate: {}", name, throwable.toString());
            }
            return false;
        }
    }

    @Override
    public void fallback(Runnable runnable, Runnable fallback) {
        if (Boolean.FALSE.equals(config.enabled())) {
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
    public <T> T fallback(Supplier<T> supplier, Supplier<T> fallback) {
        if (Boolean.FALSE.equals(config.enabled())) {
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
