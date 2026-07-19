package io.koraframework.resilient.annotation.processor.aop;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class FallbackSyncTests extends ResilientAopTestSupport {

    @Test
    void incorrectArgumentFallback() {
        compileFailed("""
            public class TestTarget {
                @Fallback(method = "fallback(missing)")
                public String call(String value) {
                    return value;
                }
                public String fallback(String value) {
                    return value;
                }
            }
            """);

        assertTrue(compileResult.isFailed());
    }

    @Test
    void incorrectSignatureFallback() {
        compileFailed("""
            public class TestTarget {
                @Fallback(method = "fallback(value)")
                public String call(String value) {
                    return value;
                }
                public String fallback(String value, String unexpected) {
                    return value;
                }
            }
            """);

        assertTrue(compileResult.isFailed());
    }

    @Test
    void syncFallback() {
        var service = compileFallbackTarget("""
            @Fallback(method = "fallback()")
            public String call() {
                if (alwaysFail) {
                    throw new IllegalStateException("Failed");
                }
                return "value";
            }
            public String fallback() {
                return "fallback";
            }
            """);

        setAlwaysFail(service, false);
        assertEquals("value", invoke(service, "call"));
        setAlwaysFail(service, true);
        assertEquals("fallback", invoke(service, "call"));
    }

    @Test
    void syncFallbackVoid() {
        var service = compileFallbackTarget("""
            @Fallback(method = "fallback()")
            public void call() {
                state = "value";
                if (alwaysFail) {
                    throw new IllegalStateException("Failed");
                }
            }
            public void fallback() {
                state = "fallback";
            }
            public String state() {
                return state;
            }
            """);

        setAlwaysFail(service, false);
        invoke(service, "call");
        assertEquals("value", invoke(service, "state"));
        setAlwaysFail(service, true);
        invoke(service, "call");
        assertEquals("fallback", invoke(service, "state"));
    }

    @Test
    void syncFallbackCheckedException() {
        var service = compileFallbackTarget("""
            @Fallback(method = "fallback()")
            public String call() throws IOException {
                if (alwaysFail) {
                    throw new IOException("Failed");
                }
                return "value";
            }
            public String fallback() {
                return "fallback";
            }
            """);

        setAlwaysFail(service, false);
        assertEquals("value", invoke(service, "call"));
        setAlwaysFail(service, true);
        assertEquals("fallback", invoke(service, "call"));
    }

    @Test
    void syncFallbackCheckedExceptionVoid() {
        var service = compileFallbackTarget("""
            @Fallback(method = "fallback()")
            public void call() throws IOException {
                state = "value";
                if (alwaysFail) {
                    throw new IOException("Failed");
                }
            }
            public void fallback() {
                state = "fallback";
            }
            public String state() {
                return state;
            }
            """);

        setAlwaysFail(service, false);
        invoke(service, "call");
        assertEquals("value", invoke(service, "state"));
        setAlwaysFail(service, true);
        invoke(service, "call");
        assertEquals("fallback", invoke(service, "state"));
    }

    @Test
    void runtimeExceptionReasonIsPassedToFallback() {
        var service = compileFallbackTarget("""
            @Fallback(method = "fallback()")
            public String call() {
                throw new IllegalArgumentException("reason-message");
            }
            public String fallback(@Fallback.Reason RuntimeException reason) {
                return reason.getClass().getSimpleName() + ":" + reason.getMessage();
            }
            """);

        assertEquals("IllegalArgumentException:reason-message", invoke(service, "call"));
    }

    @Test
    void checkedExceptionReasonIsPassedToFallback() {
        var service = compileFallbackTarget("""
            @Fallback(method = "fallback()")
            public String call() throws IOException {
                throw new IOException("checked-message");
            }
            public String fallback(@Fallback.Reason Exception reason) {
                return reason.getClass().getSimpleName() + ":" + reason.getMessage();
            }
            """);

        assertEquals("IOException:checked-message", invoke(service, "call"));
    }

    @Test
    void throwableReasonIsPassedToFallback() {
        var service = compileFallbackTarget("""
            @Fallback(method = "fallback()")
            public String call() throws Throwable {
                throw new Error("throwable-message");
            }
            public String fallback(@Fallback.Reason Throwable reason) {
                return reason.getClass().getSimpleName() + ":" + reason.getMessage();
            }
            """);

        assertEquals("Error:throwable-message", invoke(service, "call"));
    }

    private Object compileFallbackTarget(String methods) {
        return compileApp("", """
            public interface TestFallbackMarker {}
            """, """
            @Component
            @Root
            public class TestTarget {
                public boolean alwaysFail;
                public String state;
                %s
            }
            """.formatted(methods));
    }

    private void setAlwaysFail(Object service, boolean value) {
        try {
            service.getClass().getField("alwaysFail").setBoolean(service, value);
        } catch (ReflectiveOperationException e) {
            fail(e);
        }
    }
}
