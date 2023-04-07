package ru.tinkoff.kora.resilient.timeout.simple;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.resilient.timeout.TimeoutException;
import ru.tinkoff.kora.resilient.timeout.Timeouter;
import ru.tinkoff.kora.resilient.timeout.telemetry.TimeoutMetrics;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Function;

record SimpleTimeouter(String name, long delayMaxNanos, TimeoutMetrics metrics, ExecutorService executor) implements Timeouter {

    private static final Logger logger = LoggerFactory.getLogger(SimpleTimeouter.class);

    @Nonnull
    @Override
    public Duration timeout() {
        return Duration.ofNanos(delayMaxNanos);
    }

    @Override
    public void execute(@Nonnull Runnable runnable) throws TimeoutException {
        internalExecute(e -> e.submit(runnable));
    }

    @Override
    public <T> T execute(@Nonnull Callable<T> callable) throws TimeoutException {
        return internalExecute(e -> e.submit(callable));
    }

    private <T> T internalExecute(Function<ExecutorService, Future<T>> consumer) throws TimeoutException {
        try {
            return consumer.apply(executor).get(delayMaxNanos, TimeUnit.NANOSECONDS);
        } catch (ExecutionException e) {
            SimpleTimeouterUtils.doThrow(e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {
            final Duration timeout = timeout();
            logger.trace("SimpleTimeouter '{}' registered timeout after: {}", name, timeout);
            metrics.recordTimeout(name, delayMaxNanos);
            throw new TimeoutException("Timeout exceeded " + timeout);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        // is not executed
        throw new IllegalStateException("Should not happen");
    }
}
