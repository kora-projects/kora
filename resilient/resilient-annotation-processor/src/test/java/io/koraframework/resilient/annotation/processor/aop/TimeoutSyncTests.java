package io.koraframework.resilient.annotation.processor.aop;

import io.koraframework.resilient.timeout.exception.TimeoutExhaustedException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeoutSyncTests extends ResilientAopTestSupport {

    private static final String DEFAULT_CONFIG = """
        custom1 {
          duration = 10ms
        }
        """;

    private static final String DISABLED_CONFIG = """
        custom1 {
          enabled = false
          duration = 10ms
        }
        """;

    @Test
    void syncTimeout() {
        var service = compileTimeoutTarget("""
            @Timeout(TestTimeout.class)
            public String call() throws InterruptedException {
                Thread.sleep(100);
                return "OK";
            }
            """);

        assertThrows(TimeoutExhaustedException.class, () -> invoke(service, "call"));
    }

    @Test
    void syncTimeoutVoid() {
        var service = compileTimeoutTarget("""
            @Timeout(TestTimeout.class)
            public void call() throws InterruptedException {
                Thread.sleep(100);
            }
            """);

        assertThrows(TimeoutExhaustedException.class, () -> invoke(service, "call"));
    }

    @Test
    void syncTimeoutCheckedException() {
        var service = compileTimeoutTarget("""
            @Timeout(TestTimeout.class)
            public String call() throws IOException {
                sleepLong();
                return "OK";
            }
            private void sleepLong() {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            """);

        assertThrows(TimeoutExhaustedException.class, () -> invoke(service, "call"));
    }

    @Test
    void syncTimeoutCheckedExceptionVoid() {
        var service = compileTimeoutTarget("""
            @Timeout(TestTimeout.class)
            public void call() throws IOException {
                sleepLong();
            }
            private void sleepLong() {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            """);

        assertThrows(TimeoutExhaustedException.class, () -> invoke(service, "call"));
    }

    @Test
    void checkedExceptionBeforeTimeoutIsPropagated() {
        var service = compileTimeoutTarget("""
            @Timeout(TestTimeout.class)
            public void call() throws IOException {
                throw new IOException("OPS");
            }
            """);

        var ex = assertThrows(IOException.class, () -> invokeTarget(service, "call"));
        assertEquals("OPS", ex.getMessage());
    }

    @Test
    void syncTimeoutSeveralCheckedExceptions() throws Throwable {
        var service = compileTimeoutTarget("""
            @Timeout(TestTimeout.class)
            public String call(boolean fail) throws IOException, java.sql.SQLException {
                if (fail) {
                    throw new java.sql.SQLException("OPS");
                }
                return "OK";
            }
            """);

        assertEquals("OK", invokeTarget(service, "call", false));
        var ex = assertThrows(SQLException.class, () -> invokeTarget(service, "call", true));
        assertEquals("OPS", ex.getMessage());
    }

    @Test
    void syncTimeoutVoidSeveralCheckedExceptions() {
        var service = compileTimeoutTarget("""
            @Timeout(TestTimeout.class)
            public void call() throws IOException, java.util.concurrent.TimeoutException {
                throw new IOException("OPS");
            }
            """);

        var ex = assertThrows(IOException.class, () -> invokeTarget(service, "call"));
        assertEquals("OPS", ex.getMessage());
    }

    @Test
    void completionStageTimeout() {
        var service = compileTimeoutTarget("""
            @Timeout(TestTimeout.class)
            public java.util.concurrent.CompletionStage<String> call() {
                return new java.util.concurrent.CompletableFuture<>();
            }
            """);

        var result = (CompletionStage<?>) invoke(service, "call");
        var ex = assertThrows(ExecutionException.class, () -> result.toCompletableFuture().get());
        assertInstanceOf(TimeoutExhaustedException.class, ex.getCause());
    }

    @Test
    void completionStageTimeoutKeepsReturnedFuturePending() throws Throwable {
        var service = compileTimeoutTarget("""
            public final java.util.concurrent.CompletableFuture<String> shared = new java.util.concurrent.CompletableFuture<>();
            public java.util.concurrent.CompletableFuture<String> shared() {
                return shared;
            }
            @Timeout(TestTimeout.class)
            public java.util.concurrent.CompletionStage<String> call() {
                return shared;
            }
            """);

        var result = (CompletionStage<?>) invokeTarget(service, "call");
        var ex = assertThrows(ExecutionException.class, () -> result.toCompletableFuture().get());
        assertInstanceOf(TimeoutExhaustedException.class, ex.getCause());
        var shared = (CompletableFuture<?>) invoke(service, "shared");
        assertFalse(shared.isDone(), "Future returned by the method must not be completed by the timeout: " + shared);
    }

    @Test
    void disabledTimeoutSync() throws Throwable {
        var service = compileTimeoutTarget(DISABLED_CONFIG, """
            @Timeout(TestTimeout.class)
            public String call() throws InterruptedException {
                Thread.sleep(100);
                return "OK";
            }
            """);

        assertEquals("OK", invokeTarget(service, "call"));
    }

    @Test
    void disabledTimeoutCompletionStage() throws Throwable {
        var service = compileTimeoutTarget(DISABLED_CONFIG, """
            @Timeout(TestTimeout.class)
            public java.util.concurrent.CompletionStage<String> call() {
                return java.util.concurrent.CompletableFuture.supplyAsync(() -> "OK", java.util.concurrent.CompletableFuture.delayedExecutor(100, java.util.concurrent.TimeUnit.MILLISECONDS));
            }
            """);

        var result = (CompletionStage<?>) invokeTarget(service, "call");
        assertEquals("OK", result.toCompletableFuture().get());
    }

    @Test
    void disabledTimeoutCompletableFuture() throws Throwable {
        var service = compileTimeoutTarget(DISABLED_CONFIG, """
            @Timeout(TestTimeout.class)
            public java.util.concurrent.CompletableFuture<String> call() {
                return java.util.concurrent.CompletableFuture.supplyAsync(() -> "OK", java.util.concurrent.CompletableFuture.delayedExecutor(100, java.util.concurrent.TimeUnit.MILLISECONDS));
            }
            """);

        var result = (CompletableFuture<?>) invokeTarget(service, "call");
        assertEquals("OK", result.get());
    }

    private Object compileTimeoutTarget(String method) {
        return compileTimeoutTarget(DEFAULT_CONFIG, method);
    }

    private Object compileTimeoutTarget(String config, String method) {
        return compileApp(config, """
            @TimeoutSpec("custom1")
            public interface TestTimeout extends io.koraframework.resilient.timeout.Timeouter {}
            """, """
            @Component
            @Root
            public class TestTarget {
                %s
            }
            """.formatted(method));
    }
}
