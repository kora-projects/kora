package ru.tinkoff.kora.resilient.annotation.processor.aop.testdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.resilient.retry.annotation.Retry;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Root
public class RetryTarget {

    private static final Logger logger = LoggerFactory.getLogger(RetryTarget.class);
    private final AtomicInteger retryAttempts = new AtomicInteger();

    @Retry("custom1")
    public void retrySyncVoid(String arg) {
        logger.info("Retry Void executed for: {}", arg);
        if (retryAttempts.getAndDecrement() > 0) {
            throw new IllegalStateException("Ops");
        }
    }

    @Retry("custom1")
    public void retrySyncCheckedException(String arg) throws IOException {
        logger.info("Retry retrySyncCheckedException executed for: {}", arg);
        if (retryAttempts.getAndDecrement() > 0) {
            throw new IllegalStateException("Ops");
        }
    }

    @Retry("custom1")
    public String retrySync(String arg) {
        logger.info("Retry Sync executed for: {}", arg);
        if (retryAttempts.getAndDecrement() > 0) {
            throw new IllegalStateException("Ops");
        }

        return arg;
    }

    @Retry("custom2")
    public CompletionStage<String> retryStage(String arg) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Retry Future executed for: {}", arg);
            if (retryAttempts.getAndDecrement() > 0) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        });
    }

    @Retry("custom3")
    public CompletableFuture<String> retryFuture(String arg) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Retry Future executed for: {}", arg);
            if (retryAttempts.getAndDecrement() > 0) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        });
    }

    public void setRetryAttempts(int attempts) {
        retryAttempts.set(attempts);
    }

    public void reset() {
        retryAttempts.set(2);
    }
}
