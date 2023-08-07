package ru.tinkoff.kora.resilient.annotation.processor.aop.testdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.resilient.retry.annotation.Retry;
import ru.tinkoff.kora.resilient.timeout.TimeoutExhaustedException;

import java.io.IOException;
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
    public void retrySyncCheckedException(String arg) throws IOException, TimeoutExhaustedException {
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

    @Retry("custom1")
    public Mono<String> retryMono(String arg) {
        return Mono.fromCallable(() -> {
            logger.info("Retry Mono executed for: {}", arg);
            if (retryAttempts.getAndDecrement() > 0) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        });
    }

    @Retry("custom1")
    public Flux<String> retryFlux(String arg) {
        return Flux.from(Mono.fromCallable(() -> {
            logger.info("Retry Flux executed for: {}", arg);
            if (retryAttempts.getAndDecrement() > 0) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        }));
    }

    public void setRetryAttempts(int attempts) {
        retryAttempts.set(attempts);
    }

    public void reset() {
        retryAttempts.set(2);
    }
}
