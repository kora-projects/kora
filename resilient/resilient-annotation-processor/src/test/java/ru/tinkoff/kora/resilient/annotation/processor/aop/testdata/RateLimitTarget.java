package ru.tinkoff.kora.resilient.annotation.processor.aop.testdata;

import ru.tinkoff.kora.common.Component;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.resilient.ratelimiter.annotation.RateLimit;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@Root
public class RateLimitTarget {

    @RateLimit("custom1")
    public String getValueSync() {
        return "OK";
    }

    @RateLimit("custom1")
    public void getValueSyncVoid() {
        // no-op
    }

    @RateLimit("custom2")
    public CompletionStage<String> getValueStage() {
        return CompletableFuture.completedFuture("OK");
    }

    @RateLimit("custom3")
    public CompletableFuture<String> getValueFuture() {
        return CompletableFuture.completedFuture("OK");
    }
}
