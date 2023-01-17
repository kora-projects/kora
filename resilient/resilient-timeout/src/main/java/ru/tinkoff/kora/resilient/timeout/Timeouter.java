package ru.tinkoff.kora.resilient.timeout;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Timeout executor contract
 */
public interface Timeouter {

    /**
     * @return duration timeout executor is configured for
     */
    @Nonnull
    Duration timeout();

    void execute(@Nonnull Runnable runnable) throws TimeoutException;

    <T> T execute(@Nonnull Supplier<T> supplier) throws TimeoutException;
}
