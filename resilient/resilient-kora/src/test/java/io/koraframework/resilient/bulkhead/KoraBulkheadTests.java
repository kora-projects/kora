package io.koraframework.resilient.bulkhead;

import static org.junit.jupiter.api.Assertions.*;

import io.koraframework.resilient.bulkhead.exception.BulkheadFullException;
import io.koraframework.resilient.bulkhead.telemetry.impl.NoopBulkheadTelemetry;
import io.koraframework.resilient.circuitbreaker.NonCircuitableException;
import io.koraframework.resilient.common.ThrowableCallable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class KoraBulkheadTests {

    private static KoraBulkhead bulkhead(int limit) {
        return new KoraBulkhead(
            "orders",
            new $BulkheadConfig_ConfigValueMapper.BulkheadConfig_Impl(
                true, limit, null, BulkheadConfig.Type.FIXED, 0, java.time.Duration.ZERO, null
            ), NoopBulkheadTelemetry.INSTANCE
        );
    }

    @Test
    void rejectsWithoutInvokingOperationAndAllowsReuse() {
        var bulkhead = bulkhead(1);
        var permit = bulkhead.acquire();
        assertEquals(1, bulkhead.inFlight());
        var error = assertThrows(BulkheadFullException.class, () -> bulkhead.execute(() -> { fail("Rejected operation must not run"); }));
        assertEquals("orders", error.name());
        assertInstanceOf(NonCircuitableException.class, error);
        assertInstanceOf(io.koraframework.resilient.retry.NonRetryableException.class, error);
        assertEquals(1, bulkhead.rejectedCalls());
        permit.close();
        permit.close();
        assertEquals(0, bulkhead.inFlight());
        assertEquals("ok", bulkhead.execute(() -> "ok"));
        assertEquals(0, bulkhead.inFlight());
    }

    @Test
    void releasesOnCheckedExceptionAndErrorWithoutWrapping() {
        var bulkhead = bulkhead(1);
        var failure = new IOException("downstream failure");
        assertSame(
            failure,
            assertThrows(IOException.class, () -> bulkhead.execute((ThrowableCallable<String, IOException>) () -> { throw failure; }))
        );
        assertEquals(0, bulkhead.inFlight());
        var fatal = new AssertionError("fatal");
        assertSame(fatal, assertThrows(AssertionError.class, () -> bulkhead.execute(() -> { throw fatal; })));
        assertEquals(0, bulkhead.inFlight());
    }

    @Test
    void concurrentAdmissionNeverExceedsLimit() throws Exception {
        var bulkhead = bulkhead(4);
        var start = new CountDownLatch(1);
        var attempted = new CountDownLatch(32);
        var release = new CountDownLatch(1);
        var results = new ArrayList<Future<Boolean>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                for (int i = 0; i < 32; i++) {
                    results.add(executor.submit(() -> {
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        var permit = bulkhead.tryAcquire();
                        attempted.countDown();
                        if (permit == null) {
                            return false;
                        }
                        try (permit) {
                            assertTrue(release.await(5, TimeUnit.SECONDS));
                            return true;
                        }
                    }));
                }
                start.countDown();
                assertTrue(attempted.await(5, TimeUnit.SECONDS));
                assertEquals(4, bulkhead.inFlight());
                assertEquals(28, bulkhead.rejectedCalls());
            } finally {
                release.countDown();
            }
            int accepted = 0;
            for (var result : results) {
                if (result.get(5, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertEquals(4, accepted);
        }
        assertEquals(0, bulkhead.inFlight());
    }

    @Test
    void concurrentRepeatedCloseCannotInflateCapacity() throws Exception {
        var bulkhead = bulkhead(1);
        var permit = bulkhead.acquire();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = new ArrayList<Future<?>>();
            for (int i = 0; i < 32; i++) {
                results.add(executor.submit(permit::close));
            }
            for (var result : results) {
                result.get(5, TimeUnit.SECONDS);
            }
        }
        assertEquals(0, bulkhead.inFlight());
        try (var next = bulkhead.acquire()) {
            assertNull(bulkhead.tryAcquire());
            assertEquals(1, bulkhead.inFlight());
        }
    }

    @Test
    void asyncHoldsPermitUntilCompletionAndReleasesBeforePublishingResult() {
        var bulkhead = bulkhead(1);
        var source = new CompletableFuture<String>();
        var result = bulkhead.executeAsync(() -> source).toCompletableFuture();
        var observedInFlight = result.thenApply(_ -> bulkhead.inFlight());
        assertEquals(1, bulkhead.inFlight());
        assertThrows(BulkheadFullException.class, () -> bulkhead.executeAsync(() -> {
            fail("Rejected supplier must not run");
            return source;
        }));
        source.complete("ok");
        assertEquals("ok", result.join());
        assertEquals(0, observedInFlight.join());
        assertEquals(0, bulkhead.inFlight());
    }

    @Test
    void asyncFailurePreservesCauseAndReleasesPermit() {
        var bulkhead = bulkhead(1);
        var source = new CompletableFuture<String>();
        var result = bulkhead.executeAsync(() -> source).toCompletableFuture();
        var failure = new IOException("downstream failure");
        source.completeExceptionally(failure);
        assertSame(failure, assertThrows(java.util.concurrent.CompletionException.class, result::join).getCause());
        assertEquals(0, bulkhead.inFlight());
    }

    @Test
    void cancellingReturnedFutureDoesNotReleaseStillRunningSource() {
        var bulkhead = bulkhead(1);
        var source = new CompletableFuture<String>();
        var result = bulkhead.executeAsync(() -> source).toCompletableFuture();
        assertTrue(result.cancel(true));
        assertFalse(source.isCancelled());
        assertEquals(1, bulkhead.inFlight());
        assertNull(bulkhead.tryAcquire());
        source.complete("finished");
        assertEquals(0, bulkhead.inFlight());
        assertTrue(result.isCancelled());
    }

    @Test
    void completedStageAndSourceCancellationReleasePermit() {
        var bulkhead = bulkhead(1);
        assertEquals("ok", bulkhead.executeAsync(() -> CompletableFuture.completedFuture("ok")).toCompletableFuture().join());
        assertEquals(0, bulkhead.inFlight());
        var source = new CompletableFuture<String>();
        var result = bulkhead.executeAsync(() -> source).toCompletableFuture();
        source.cancel(false);
        assertTrue(result.isCancelled());
        assertEquals(0, bulkhead.inFlight());
    }

    @Test
    void failingOrNullAsyncSupplierCannotLeakCapacity() {
        var bulkhead = bulkhead(1);
        var failure = new IOException("failed to start");
        assertSame(failure, assertThrows(IOException.class, () -> bulkhead.executeAsync(() -> { throw failure; })));
        assertEquals(0, bulkhead.inFlight());
        assertThrows(NullPointerException.class, () -> bulkhead.executeAsync(() -> null));
        assertEquals(0, bulkhead.inFlight());
    }

    @Test
    void differentInstancesHaveIndependentBudgets() {
        var orders = bulkhead(1);
        var payments = bulkhead(1);
        try (var orderPermit = orders.acquire(); var paymentPermit = payments.acquire()) {
            assertNull(orders.tryAcquire());
            assertEquals(1, payments.inFlight());
            assertEquals(0, payments.rejectedCalls());
        }
    }

    @Test
    void disabledBulkheadBypassesAdmissionAndCounters() {
        var bulkhead = new KoraBulkhead(
            "orders",
            new $BulkheadConfig_ConfigValueMapper.BulkheadConfig_Impl(
                false, 1, null, BulkheadConfig.Type.FIXED, 0, java.time.Duration.ZERO, null
            ), NoopBulkheadTelemetry.INSTANCE
        );
        try (var first = bulkhead.acquire(); var second = bulkhead.acquire()) {
            assertEquals("ok", bulkhead.execute(() -> "ok"));
            assertEquals(0, bulkhead.inFlight());
            assertEquals(0, bulkhead.rejectedCalls());
        }
    }

    @Test
    void validatesConfigurationAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> bulkhead(0));
        assertThrows(IllegalArgumentException.class, () -> bulkhead(-1));
        assertThrows(
            IllegalArgumentException.class,
            () -> new KoraBulkhead(
                " ",
                new $BulkheadConfig_ConfigValueMapper.BulkheadConfig_Impl(
                    true, 1, null, BulkheadConfig.Type.FIXED, 0, java.time.Duration.ZERO, null
                ), NoopBulkheadTelemetry.INSTANCE
            )
        );
    }
}
