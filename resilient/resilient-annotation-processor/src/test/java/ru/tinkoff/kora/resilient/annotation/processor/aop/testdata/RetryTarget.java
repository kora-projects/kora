package ru.tinkoff.kora.resilient.annotation.processor.aop.testdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
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
    private final AtomicInteger stopFailAfterAttempts = new AtomicInteger();
    private final AtomicInteger retryAttempts = new AtomicInteger();

    @Retry("custom1")
    public void retrySyncVoid(String arg) {
        logger.info("Retry Void executed for: {}", arg);
        if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
            throw new IllegalStateException("Ops");
        }
    }

    @Retry("custom1")
    public void retrySyncCheckedException(String arg) throws IOException {
        logger.info("Retry retrySyncCheckedException executed for: {}", arg);
        if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
            throw new IOException("Ops");
        }
    }

    @Retry("custom1")
    public String retrySync(String arg) {
        logger.info("Retry Sync executed for: {}", arg);
        if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
            throw new IllegalStateException("Ops");
        }

        return arg;
    }

    @Retry("custom2")
    public CompletionStage<String> retryStage(String arg) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Retry Future executed for: {}", arg);
            if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        });
    }

    @Retry("custom3")
    public CompletableFuture<String> retryFuture(String arg) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Retry Future executed for: {}", arg);
            if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        });
    }

    @Retry("custom4")
    public Mono<String> retryMono(String arg) {
        return Mono.fromCallable(() -> {
            logger.info("Retry Mono executed for: {}", arg);
            if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        });
    }

    @Retry("custom5")
    public Flux<String> retryFlux(String arg) {
        return Flux.from(Mono.fromCallable(() -> {
            logger.info("Retry Flux executed for: {}", arg);
            if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        }));
    }

    @Retry("customZeroAttempts")
    public String retrySyncZeroAttempts(String arg) {
        logger.info("Retry Sync executed for: {}", arg);
        if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
            throw new IllegalStateException("Ops");
        }

        return arg;
    }

    @Retry("customZeroAttempts")
    public CompletionStage<String> retryStageZeroAttempts(String arg) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Retry Future executed for: {}", arg);
            if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        });
    }

    @Retry("customZeroAttempts")
    public CompletableFuture<String> retryFutureZeroAttempts(String arg) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Retry Future executed for: {}", arg);
            if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        });
    }

    @Retry("customZeroAttempts")
    public Mono<String> retryMonoZeroAttempts(String arg) {
        return Mono.fromCallable(() -> {
            logger.info("Retry Mono executed for: {}", arg);
            if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        });
    }

    @Retry("customZeroAttempts")
    public Flux<String> retryFluxZeroAttempts(String arg) {
        return Flux.from(Mono.fromCallable(() -> {
            logger.info("Retry Flux executed for: {}", arg);
            if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        }));
    }

    @Retry("customDisabled")
    public String retrySyncDisabled(String arg) {
        logger.info("Retry Sync executed for: {}", arg);
        if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
            throw new IllegalStateException("Ops");
        }

        return arg;
    }

    @Retry("customDisabled")
    public CompletionStage<String> retryStageDisabled(String arg) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Retry Future executed for: {}", arg);
            if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        });
    }

    @Retry("customDisabled")
    public CompletableFuture<String> retryFutureDisabled(String arg) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Retry Future executed for: {}", arg);
            if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        });
    }

    @Retry("customDisabled")
    public Mono<String> retryMonoDisabled(String arg) {
        return Mono.fromCallable(() -> {
            logger.info("Retry Mono executed for: {}", arg);
            if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        });
    }

    @Retry("customDisabled")
    public Flux<String> retryFluxDisabled(String arg) {
        return Flux.from(Mono.fromCallable(() -> {
            logger.info("Retry Flux executed for: {}", arg);
            if (!(retryAttempts.incrementAndGet() >= stopFailAfterAttempts.get())) {
                throw new IllegalStateException("Ops");
            }

            return arg;
        }));
    }

    public void setFailAttempts(int attempts) {
        retryAttempts.set(-1);
        stopFailAfterAttempts.set(attempts);
    }

    public int getRetryAttempts() {
        return retryAttempts.get();
    }
}
