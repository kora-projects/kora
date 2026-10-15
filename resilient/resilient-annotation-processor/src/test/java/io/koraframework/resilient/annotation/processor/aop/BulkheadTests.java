package io.koraframework.resilient.annotation.processor.aop;

import static org.junit.jupiter.api.Assertions.*;

import io.koraframework.resilient.bulkhead.Bulkhead;
import io.koraframework.resilient.bulkhead.exception.BulkheadFullException;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

class BulkheadTests extends ResilientAopTestSupport {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
                import io.koraframework.resilient.bulkhead.annotation.Bulkheaded;
                import io.koraframework.resilient.bulkhead.annotation.BulkheadSpec;
                import java.util.concurrent.CompletableFuture;
                import java.util.concurrent.CompletionStage;
                """;
    }

    private Object target(String methods) {
        return target("orders { maxConcurrentCalls = 1 }", methods);
    }

    private Object target(String config, String methods) {
        return compileApp(config, """
                @BulkheadSpec("orders")
                public interface OrdersBulkhead extends io.koraframework.resilient.bulkhead.Bulkhead {}
                """, """
                @Component
                @Root
                public class TestTarget {
                    private final OrdersBulkhead bulkhead;
                    public TestTarget(OrdersBulkhead bulkhead) { this.bulkhead = bulkhead; }
                    public OrdersBulkhead limiter() { return bulkhead; }
                    %s
                }
                """.formatted(methods));
    }

    @Test
    void generatedMethodsHonorAdaptiveQueueConfigAndUseCallersThread() throws Throwable {
        var service = target("""
                orders {
                    maxConcurrentCalls = 2
                    type = AIMD
                    adaptive { initialLimit = 1 }
                    maxQueuedCalls = 1
                    maxWaitDuration = 10s
                }
                """, """
                public String lastThread;
                @Bulkheaded(OrdersBulkhead.class)
                public String thread() { return Thread.currentThread().getName(); }
                @Bulkheaded(OrdersBulkhead.class)
                public void run() { lastThread = Thread.currentThread().getName(); }
                public String lastThread() { return lastThread; }
                @Bulkheaded(OrdersBulkhead.class)
                public String failure() throws IOException { throw new IOException("downstream failure"); }
                @Bulkheaded(OrdersBulkhead.class)
                public CompletionStage<String> stage(CompletableFuture<String> source) { return source; }
                """);
        var limiter = (Bulkhead) invokeTarget(service, "limiter");
        try {
            assertEquals(1, limiter.currentLimit());
            assertEquals(2, limiter.maxConcurrentCalls());
            assertEquals(Thread.currentThread().getName(), invokeTarget(service, "thread"));
            invokeTarget(service, "run");
            assertEquals(Thread.currentThread().getName(), invokeTarget(service, "lastThread"));
            assertEquals("downstream failure", assertThrows(IOException.class, () -> invokeTarget(service, "failure")).getMessage());
            var permit = limiter.acquire();
            var source = new CompletableFuture<String>();
            var result = (CompletionStage<?>) invokeTarget(service, "stage", source);
            assertEquals(1, limiter.queueLength());
            assertFalse(result.toCompletableFuture().isDone());
            permit.close();
            source.complete("ok");
            assertEquals("ok", result.toCompletableFuture().get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(0, limiter.inFlight());
        } finally {
            ((io.koraframework.application.graph.Lifecycle) limiter).release();
        }
    }

    @Test
    void syncAndVoidMethodsReleaseCapacity() throws Throwable {
        var service = target("""
                @Bulkheaded(OrdersBulkhead.class)
                public String call(String value) { return value; }
                @Bulkheaded(OrdersBulkhead.class)
                public void run() {}
                """);
        assertEquals("ok", invokeTarget(service, "call", "ok"));
        invokeTarget(service, "run");
        assertEquals(0, ((Bulkhead) invokeTarget(service, "limiter")).inFlight());
    }

    @Test
    void sameSpecRejectsBeforeMethodInvocation() throws Throwable {
        var service = target("""
                @Bulkheaded(OrdersBulkhead.class)
                public String call() { throw new AssertionError("must not run"); }
                """);
        var limiter = (Bulkhead) invokeTarget(service, "limiter");
        try (var permit = limiter.acquire()) {
            assertThrows(BulkheadFullException.class, () -> invokeTarget(service, "call"));
            assertEquals(1, limiter.inFlight());
            assertEquals(1, limiter.rejectedCalls());
        }
    }

    @Test
    void checkedFailureReleasesCapacityAndPreservesException() throws Throwable {
        var service = target("""
                @Bulkheaded(OrdersBulkhead.class)
                public String call() throws IOException { throw new IOException("failure"); }
                """);
        assertEquals("failure", assertThrows(IOException.class, () -> invokeTarget(service, "call")).getMessage());
        assertEquals(0, ((Bulkhead) invokeTarget(service, "limiter")).inFlight());
    }

    @Test
    void disabledSpecBypassesLimit() throws Throwable {
        var service = target("orders { enabled = false, maxConcurrentCalls = 1 }", """
                @Bulkheaded(OrdersBulkhead.class)
                public int call() { return 42; }
                """);
        var limiter = (Bulkhead) invokeTarget(service, "limiter");
        try (var permit = limiter.acquire()) {
            assertEquals(42, invokeTarget(service, "call"));
            assertEquals(0, limiter.inFlight());
        }
    }

    @Test
    void completionStageHoldsPermitUntilSourceTerminates() throws Throwable {
        var service = target("""
                @Bulkheaded(OrdersBulkhead.class)
                public CompletionStage<String> call(CompletableFuture<String> source) { return source; }
                """);
        var limiter = (Bulkhead) invokeTarget(service, "limiter");
        var source = new CompletableFuture<String>();
        var result = (CompletionStage<?>) invokeTarget(service, "call", source);
        assertEquals(1, limiter.inFlight());
        assertThrows(BulkheadFullException.class, () -> invokeTarget(service, "call", source));
        source.complete("ok");
        assertEquals("ok", result.toCompletableFuture().join());
        assertEquals(0, limiter.inFlight());
    }

    @Test
    void completableFutureCancellationDoesNotReleaseRunningSource() throws Throwable {
        var service = target("""
                @Bulkheaded(OrdersBulkhead.class)
                public CompletableFuture<String> call(CompletableFuture<String> source) { return source; }
                """);
        var limiter = (Bulkhead) invokeTarget(service, "limiter");
        var source = new CompletableFuture<String>();
        var result = (CompletableFuture<?>) invokeTarget(service, "call", source);
        result.cancel(false);
        assertFalse(source.isCancelled());
        assertEquals(1, limiter.inFlight());
        source.complete("ok");
        assertEquals(0, limiter.inFlight());
    }

    @Test
    void defaultRetryAndCircuitBreakerDoNotTreatSaturationAsDownstreamFailure() throws Throwable {
        var service = target("""
                orders { maxConcurrentCalls = 1 }
                retry { delay = 1ms, attempts = 3 }
                breaker {
                    type = FIXED_WINDOW
                    countBased { windowSize = 1 }
                    minimumRequiredCalls = 1
                    failureRateThreshold = 100
                    permittedCallsInHalfOpenState = 1
                    waitDurationInOpenState = 1s
                }
                """, """
                @RetrySpec("retry")
                public interface OrdersRetry extends io.koraframework.resilient.retry.Retry {}
                @io.koraframework.resilient.circuitbreaker.annotation.CircuitBreakerSpec("breaker")
                public interface OrdersCircuitBreaker extends io.koraframework.resilient.circuitbreaker.CircuitBreaker {}
                @Retryable(OrdersRetry.class)
                public String retryCall() { return bulkhead.execute(() -> "ok"); }
                @io.koraframework.resilient.circuitbreaker.annotation.CircuitBreakable(OrdersCircuitBreaker.class)
                public String circuitCall() { return bulkhead.execute(() -> "ok"); }
                """);
        var limiter = (Bulkhead) invokeTarget(service, "limiter");
        try (var permit = limiter.acquire()) {
            assertThrows(BulkheadFullException.class, () -> invokeTarget(service, "retryCall"));
            assertEquals(1, limiter.rejectedCalls());
            assertThrows(BulkheadFullException.class, () -> invokeTarget(service, "circuitCall"));
        }
        assertEquals("ok", invokeTarget(service, "retryCall"));
        assertEquals("ok", invokeTarget(service, "circuitCall"));
    }

    @Test
    void unsupportedFutureAndPublisherHaveDiagnostics() {
        for (String returnType : new String[] {
                "java.util.concurrent.Future<String>", "java.util.concurrent.Flow.Publisher<String>"
        }) {
            compileFailed(app("orders { maxConcurrentCalls = 1 }"), """
                    @BulkheadSpec("orders")
                    public interface OrdersBulkhead extends io.koraframework.resilient.bulkhead.Bulkhead {}
                    """, """
                    @Component
                    @Root
                    public class TestTarget {
                        @Bulkheaded(OrdersBulkhead.class)
                        public %s call() { return null; }
                    }
                    """.formatted(returnType));
            assertTrue(compileResult.isFailed());
            assertTrue(compileResult.errors().stream().anyMatch(error -> error.getMessage(null).contains("@Bulkheaded")));
        }
    }

    @Test
    void invalidSpecHasDiagnostics() {
        compileFailed("""
                @BulkheadSpec("orders")
                public interface InvalidBulkhead {}
                """);
        assertTrue(compileResult.isFailed());
        assertTrue(compileResult.errors().stream().anyMatch(error -> error.getMessage(null).contains("must extend")));
    }

    @Test
    void blankConfigPathHasDiagnostics() {
        compileFailed("""
                @BulkheadSpec(" ")
                public interface InvalidBulkhead extends io.koraframework.resilient.bulkhead.Bulkhead {}
                """);
        assertTrue(compileResult.isFailed());
        assertTrue(compileResult.errors().stream().anyMatch(error -> error.getMessage(null).contains("blank config path")));
    }
}
