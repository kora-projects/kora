package io.koraframework.resilient.annotation.processor.aop;

import io.koraframework.resilient.timeout.exception.TimeoutExhaustedException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeoutSyncTests extends ResilientAopTestSupport {

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

    private Object compileTimeoutTarget(String method) {
        return compileApp("""
            custom1 {
              duration = 10ms
            }
            """, """
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
