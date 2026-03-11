package io.koraframework.resilient.annotation.processor.aop.testdata;

import io.koraframework.common.Component;
import io.koraframework.common.annotation.Root;
import io.koraframework.resilient.circuitbreaker.annotation.CircuitBreaker;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@Root
public class CircuitBreakerTarget {

    public boolean alwaysFail = true;

    @CircuitBreaker("custom1")
    public String getValueSync() {
        if (alwaysFail)
            throw new IllegalStateException("Failed");

        return "OK";
    }

    @CircuitBreaker("custom1")
    public void getValueSyncVoid() {
        if (alwaysFail)
            throw new IllegalStateException("Failed");
    }

    @CircuitBreaker("custom1")
    public void getValueSyncVoidCheckedException() throws IOException {
        if (alwaysFail)
            throw new IllegalStateException("Failed");
    }

    @CircuitBreaker("custom1")
    public String getValueSyncCheckedException() throws IOException {
        if (alwaysFail)
            throw new IllegalStateException("Failed");

        return "OK";
    }

    @CircuitBreaker("custom2")
    public CompletionStage<String> getValueStage() {
        if (alwaysFail)
            return CompletableFuture.failedFuture(new IllegalStateException("Failed"));

        return CompletableFuture.completedFuture("OK");
    }

    @CircuitBreaker("custom3")
    public CompletableFuture<String> getValueFuture() {
        if (alwaysFail)
            return CompletableFuture.failedFuture(new IllegalStateException("Failed"));

        return CompletableFuture.completedFuture("OK");
    }
}
