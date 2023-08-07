package ru.tinkoff.kora.resilient.timeout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Function;

record KoraTimeout(String name, long delayMaxNanos, TimeoutMetrics metrics, ExecutorService executor) implements Timeout {

    private static final Logger logger = LoggerFactory.getLogger(KoraTimeout.class);

    @Nonnull
    @Override
    public Duration timeout() {
        return Duration.ofNanos(delayMaxNanos);
    }

    @Override
    public void execute(@Nonnull Runnable runnable) throws TimeoutExhaustedException {
        internalExecute(e -> e.submit(runnable));
    }

    @Override
    public <T> T execute(@Nonnull Callable<T> callable) throws TimeoutExhaustedException {
        return internalExecute(e -> e.submit(callable));
    }

    private <T> T internalExecute(Function<ExecutorService, Future<T>> consumer) throws TimeoutExhaustedException {
        try {
            if (logger.isTraceEnabled()) {
                final Duration timeout = timeout();
                logger.trace("KoraTimeout '{}' starting await for {}", name, timeout);
            }

            return consumer.apply(executor).get(delayMaxNanos, TimeUnit.NANOSECONDS);
        } catch (ExecutionException e) {
            KoraTimeouterUtils.doThrow(e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {
            final Duration timeout = timeout();
            logger.debug("KoraTimeout '{}' registered timeout after: {}", name, timeout);
            metrics.recordTimeout(name, delayMaxNanos);
            throw new TimeoutExhaustedException(name, "Timeout exceeded " + timeout);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        // is not executed
        throw new IllegalStateException("Should not happen");
    }
}
