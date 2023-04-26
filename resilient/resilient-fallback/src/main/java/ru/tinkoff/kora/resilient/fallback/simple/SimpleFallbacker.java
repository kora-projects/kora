package ru.tinkoff.kora.resilient.fallback.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.resilient.fallback.FallbackFailurePredicate;
import ru.tinkoff.kora.resilient.fallback.Fallbacker;
import ru.tinkoff.kora.resilient.fallback.telemetry.FallbackMetrics;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

final class SimpleFallbacker implements Fallbacker {

    private static final Logger logger = LoggerFactory.getLogger(SimpleFallbacker.class);

    private final String name;
    private final FallbackMetrics metrics;
    private final FallbackFailurePredicate failurePredicate;

    SimpleFallbacker(String name, FallbackMetrics metrics, FallbackFailurePredicate failurePredicate) {
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
