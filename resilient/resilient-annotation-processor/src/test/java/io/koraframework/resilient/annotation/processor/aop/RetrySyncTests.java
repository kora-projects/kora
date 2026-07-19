package io.koraframework.resilient.annotation.processor.aop;

import io.koraframework.resilient.retry.exception.RetryExhaustedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetrySyncTests extends ResilientAopTestSupport {

    @Test
    void syncVoidRetrySuccess() {
        var service = compileRetryTarget("""
            @Retryable(TestRetry.class)
            public void call() {
                attempts++;
            }
            """);

        invoke(service, "call");

        assertEquals(1, invoke(service, "attempts"));
    }

    @Test
    void syncVoidRetryFail() {
        var service = compileRetryTarget("""
            @Retryable(TestRetry.class)
            public void call() {
                attempts++;
                throw new IllegalStateException("Failed");
            }
            """);

        var ex = assertThrows(RetryExhaustedException.class, () -> invoke(service, "call"));

        assertNotNull(ex.getMessage());
        assertEquals(3, invoke(service, "attempts"));
    }

    @Test
    void syncRetrySuccess() {
        var service = compileRetryTarget("""
            @Retryable(TestRetry.class)
            public String call(String value) {
                attempts++;
                return value;
            }
            """);

        assertEquals("1", invoke(service, "call", "1"));
        assertEquals(1, invoke(service, "attempts"));
    }

    @Test
    void syncRetryFail() {
        var service = compileRetryTarget("""
            @Retryable(TestRetry.class)
            public String call(String value) {
                attempts++;
                throw new IllegalStateException("Failed");
            }
            """);

        var ex = assertThrows(RetryExhaustedException.class, () -> invoke(service, "call", "1"));

        assertNotNull(ex.getMessage());
        assertEquals(3, invoke(service, "attempts"));
    }

    @Test
    void syncRetryCheckedSuccess() {
        var service = compileRetryTarget("""
            @Retryable(TestRetry.class)
            public String call(String value) throws IOException {
                attempts++;
                return value;
            }
            """);

        assertEquals("1", invoke(service, "call", "1"));
        assertEquals(1, invoke(service, "attempts"));
    }

    @Test
    void syncRetryCheckedFail() {
        var service = compileRetryTarget("""
            @Retryable(TestRetry.class)
            public String call(String value) throws IOException {
                attempts++;
                throw new IllegalStateException("Failed");
            }
            """);

        var ex = assertThrows(RetryExhaustedException.class, () -> invoke(service, "call", "1"));

        assertNotNull(ex.getMessage());
        assertEquals(3, invoke(service, "attempts"));
    }

    @Test
    void syncRetryZeroSuccess() {
        var service = compileRetryTarget("TestRetryZeroAttempts", """
            @Retryable(TestRetryZeroAttempts.class)
            public String call(String value) {
                attempts++;
                return value;
            }
            """);

        assertEquals("1", invoke(service, "call", "1"));
        assertEquals(1, invoke(service, "attempts"));
    }

    @Test
    void syncRetryZeroFail() {
        var service = compileRetryTarget("TestRetryZeroAttempts", """
            @Retryable(TestRetryZeroAttempts.class)
            public String call(String value) {
                attempts++;
                throw new IllegalStateException("Failed");
            }
            """);

        assertThrows(IllegalStateException.class, () -> invoke(service, "call", "1"));
        assertEquals(1, invoke(service, "attempts"));
    }

    @Test
    void syncRetryDisabledSuccess() {
        var service = compileRetryTarget("TestRetryDisabled", """
            @Retryable(TestRetryDisabled.class)
            public String call(String value) {
                attempts++;
                return value;
            }
            """);

        assertEquals("1", invoke(service, "call", "1"));
        assertEquals(1, invoke(service, "attempts"));
    }

    @Test
    void syncRetryDisabledFail() {
        var service = compileRetryTarget("TestRetryDisabled", """
            @Retryable(TestRetryDisabled.class)
            public String call(String value) {
                attempts++;
                throw new IllegalStateException("Failed");
            }
            """);

        assertThrows(IllegalStateException.class, () -> invoke(service, "call", "1"));
        assertEquals(1, invoke(service, "attempts"));
    }

    private Object compileRetryTarget(String method) {
        return compileRetryTarget("TestRetry", method);
    }

    private Object compileRetryTarget(String retryType, String method) {
        return compileApp(retryConfig(), retryInterface(retryType), """
            @Component
            @Root
            public class TestTarget {
                public int attempts = 0;
                public int attempts() {
                    return attempts;
                }
                %s
            }
            """.formatted(method));
    }

    private String retryInterface(String retryType) {
        var configPath = switch (retryType) {
            case "TestRetryZeroAttempts" -> "customZeroAttempts";
            case "TestRetryDisabled" -> "customDisabled";
            default -> "custom1";
        };
        return """
            @RetrySpec("%s")
            public interface %s extends io.koraframework.resilient.retry.Retry {}
            """.formatted(configPath, retryType);
    }

    private String retryConfig() {
        return """
            custom1 {
              delay = 1ms
              attempts = 2
            }
            customZeroAttempts {
              delay = 1ms
              attempts = 0
            }
            customDisabled {
              enabled = false
              delay = 1ms
              attempts = 2
            }
            """;
    }
}
