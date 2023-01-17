package ru.tinkoff.kora.resilient.fallback.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.resilient.fallback.FallbackException;
import ru.tinkoff.kora.resilient.fallback.FallbackFailurePredicate;
import ru.tinkoff.kora.resilient.fallback.Fallbacker;
import ru.tinkoff.kora.resilient.fallback.telemetry.FallbackMetrics;

import javax.annotation.Nonnull;
import java.util.concurrent.Callable;

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
            logger.trace("Recorded possible Fallback named: {}", name);
            metrics.recordExecute(name, throwable);
            return true;
        } else {
            logger.trace("Recorded possible Fallback named '{}' failure predicate didn't pass exception for: {}", name, throwable);
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
    public <T> T fallback(@Nonnull Callable<T> supplier, @Nonnull Callable<T> fallback) {
        try {
            return supplier.call();
        } catch (Throwable e) {
            if (canFallback(e)) {
                try {
                    return fallback.call();
                } catch (Exception ex) {
                    throw new FallbackException(ex, name);
                }
            } else {
                throw new FallbackException(e, name);
            }
        }
    }
}
