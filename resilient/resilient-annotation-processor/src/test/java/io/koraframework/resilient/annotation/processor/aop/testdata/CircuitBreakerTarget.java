package io.koraframework.resilient.annotation.processor.aop.testdata;

import io.koraframework.common.annotation.Component;
import io.koraframework.common.annotation.Root;
import io.koraframework.resilient.circuitbreaker.annotation.CircuitBreakable;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
@Root
public class CircuitBreakerTarget {

    public boolean alwaysFail = true;

    @CircuitBreakable(TestCircuitBreaker.class)
    public String getValueSync() {
        if (alwaysFail)
            throw new IllegalStateException("Failed");

        return "OK";
    }

    @CircuitBreakable(TestCircuitBreaker.class)
    public void getValueSyncVoid() {
        if (alwaysFail)
            throw new IllegalStateException("Failed");
    }

    @CircuitBreakable(TestCircuitBreaker.class)
    public void getValueSyncVoidCheckedException() throws IOException {
        if (alwaysFail)
            throw new IllegalStateException("Failed");
    }

    @CircuitBreakable(TestCircuitBreaker.class)
    public String getValueSyncCheckedException() throws IOException {
        if (alwaysFail)
            throw new IllegalStateException("Failed");

        return "OK";
    }

    @CircuitBreakable(TestCircuitBreaker.class)
    public CompletionStage<String> getValueStage() {
        if (alwaysFail)
            return CompletableFuture.failedFuture(new IllegalStateException("Failed"));

        return CompletableFuture.completedFuture("OK");
    }

    @CircuitBreakable(TestCircuitBreaker.class)
    public CompletableFuture<String> getValueFuture() {
        if (alwaysFail)
            return CompletableFuture.failedFuture(new IllegalStateException("Failed"));

        return CompletableFuture.completedFuture("OK");
    }
}
