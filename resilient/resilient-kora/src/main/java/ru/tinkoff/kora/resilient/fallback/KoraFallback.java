package ru.tinkoff.kora.resilient.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

final class KoraFallback implements Fallback {

    private static final Logger logger = LoggerFactory.getLogger(KoraFallback.class);

    private final String name;
    private final FallbackMetrics metrics;
    private final FallbackPredicate failurePredicate;

    KoraFallback(String name, FallbackMetrics metrics, FallbackPredicate failurePredicate) {
        this.name = name;
        this.metrics = metrics;
        this.failurePredicate = failurePredicate;
    }

    @Override
    public boolean canFallback(Throwable throwable) {
        if (failurePredicate.test(throwable)) {
            logger.debug("Initiating Fallback '{}' due to: {}", name, throwable.getClass().getCanonicalName());
            metrics.recordExecute(name, throwable);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void fallback(@Nonnull Runnable runnable, @Nonnull Runnable fallback) {
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
