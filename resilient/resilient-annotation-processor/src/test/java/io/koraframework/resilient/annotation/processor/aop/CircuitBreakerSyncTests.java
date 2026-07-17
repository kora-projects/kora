package io.koraframework.resilient.annotation.processor.aop;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.resilient.annotation.processor.CircuitBreakerAnnotationProcessor;
import io.koraframework.resilient.circuitbreaker.CircuitBreaker;
import io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class CircuitBreakerSyncTests extends AbstractAnnotationProcessorTest {

    @Override
    protected String commonImports() {
        return super.commonImports() + """
            import com.typesafe.config.ConfigFactory;
            import io.koraframework.config.common.Config;
            import io.koraframework.config.common.mapper.ConfigValueMapperModule;
            import io.koraframework.config.common.origin.SimpleConfigOrigin;
            import io.koraframework.config.hocon.HoconConfigFactory;
            import io.koraframework.resilient.circuitbreaker.CircuitBreakerModule;
            import io.koraframework.resilient.circuitbreaker.annotation.CircuitBreaker;
            import java.io.IOException;
            import java.util.concurrent.CompletableFuture;
            import java.util.concurrent.CompletionStage;
            """;
    }

    @Test
    void syncCircuitBreaker() {
        var service = compileApp("""
            @Component
            @Root
            public class TestTarget {
                @CircuitBreaker("resilient.circuitbreaker.custom1")
                public String getValueSync() {
                    throw new IllegalStateException("Failed");
                }
            }
            """);

        assertCircuitBreaker(service, "getValueSync");
    }

    @Test
    void voidCircuitBreaker() {
        var service = compileApp("""
            @Component
            @Root
            public class TestTarget {
                @CircuitBreaker("resilient.circuitbreaker.custom1")
                public void getValueSyncVoid() {
                    throw new IllegalStateException("Failed");
                }
            }
            """);

        assertCircuitBreaker(service, "getValueSyncVoid");
    }

    @Test
    void voidCircuitBreakerCheckedException() {
        var service = compileApp("""
            @Component
            @Root
            public class TestTarget {
                @CircuitBreaker("resilient.circuitbreaker.custom1")
                public void getValueSyncVoidCheckedException() throws IOException {
                    throw new IllegalStateException("Failed");
                }
            }
            """);

        assertCircuitBreaker(service, "getValueSyncVoidCheckedException");
    }

    @Test
    void syncCircuitBreakerCheckedException() {
        var service = compileApp("""
            @Component
            @Root
            public class TestTarget {
                @CircuitBreaker("resilient.circuitbreaker.custom1")
                public String getValueSyncCheckedException() throws IOException {
                    throw new IllegalStateException("Failed");
                }
            }
            """);

        assertCircuitBreaker(service, "getValueSyncCheckedException");
    }

    @Test
    void completionStageCircuitBreaker() {
        var service = compileApp("""
            @Component
            @Root
            public class TestTarget {
                @CircuitBreaker("resilient.circuitbreaker.custom1")
                public CompletionStage<String> getValueStage() {
                    return CompletableFuture.failedFuture(new IllegalStateException("Failed"));
                }
            }
            """);

        assertFutureCircuitBreaker(service, "getValueStage");
    }

    @Test
    void completableFutureCircuitBreaker() {
        var service = compileApp("""
            @Component
            @Root
            public class TestTarget {
                @CircuitBreaker("resilient.circuitbreaker.custom1")
                public CompletableFuture<String> getValueFuture() {
                    return CompletableFuture.failedFuture(new IllegalStateException("Failed"));
                }
            }
            """);

        assertFutureCircuitBreaker(service, "getValueFuture");
    }

    @Test
    void sameConfigPathUsesSingleCircuitBreakerComponent() {
        compile(List.of(new KoraAppProcessor(), new CircuitBreakerAnnotationProcessor(), new AopAnnotationProcessor()), app(), """
            @Component
            @Root
            public class TestTarget1 {
                @CircuitBreaker("resilient.circuitbreaker.custom1")
                public String getValue() {
                    return "1";
                }
            }
            """, """
            @Component
            @Root
            public class TestTarget2 {
                @CircuitBreaker("resilient.circuitbreaker.custom1")
                public String getValue() {
                    return "2";
                }
            }
            """);
        compileResult.assertSuccess();

        var graph = loadGraph("AppWithConfig");
        assertEquals(1, graph.findAllByType(CircuitBreaker.class).size());
    }

    @Test
    void rootConfigPathIsAllowed() {
        compile(List.of(new KoraAppProcessor(), new CircuitBreakerAnnotationProcessor(), new AopAnnotationProcessor()), appWithRootConfig(), """
            @Component
            @Root
            public class TestTarget {
                @CircuitBreaker("payment")
                public String getValue() {
                    throw new IllegalStateException("Failed");
                }
            }
            """);
        compileResult.assertSuccess();

        var graph = loadGraph("AppWithConfig");
        var service = graph.findByType(loadClass("TestTarget"));
        assertNotNull(service);
        assertCircuitBreaker(service, "getValue");
    }

    @Test
    void generatedTagNameCollisionFails() {
        compile(List.of(new KoraAppProcessor(), new CircuitBreakerAnnotationProcessor(), new AopAnnotationProcessor()), app(), """
            @Component
            @Root
            public class TestTarget1 {
                @CircuitBreaker("resilient.circuitbreaker.foo-bar")
                public String getValue() {
                    return "1";
                }
            }
            """, """
            @Component
            @Root
            public class TestTarget2 {
                @CircuitBreaker("resilient.circuitbreaker.foo.bar")
                public String getValue() {
                    return "2";
                }
            }
            """);

        assertTrue(compileResult.isFailed());
        assertTrue(compileResult.errors().stream()
            .anyMatch(e -> e.getMessage(null).contains("generate the same tag")));
    }

    private Object compileApp(String target) {
        compile(List.of(new KoraAppProcessor(), new CircuitBreakerAnnotationProcessor(), new AopAnnotationProcessor()), app(), target);
        compileResult.assertSuccess();

        var graph = loadGraph("AppWithConfig");
        var service = graph.findByType(loadClass("TestTarget"));
        assertNotNull(service);
        return service;
    }

    private void assertCircuitBreaker(Object service, String methodName) {
        try {
            invoke(service, methodName);
            fail("Should not happen");
        } catch (IllegalStateException e) {
            assertNotNull(e.getMessage());
        }

        try {
            invoke(service, methodName);
            fail("Should not happen");
        } catch (CallNotPermittedException ex) {
            assertNotNull(ex.getMessage());
        }
    }

    private void assertFutureCircuitBreaker(Object service, String methodName) {
        var first = assertInstanceOf(CompletionStage.class, invoke(service, methodName));
        var firstError = assertThrows(CompletionException.class, () -> first.toCompletableFuture().join());
        assertInstanceOf(IllegalStateException.class, firstError.getCause());

        var second = assertInstanceOf(CompletionStage.class, invoke(service, methodName));
        var secondError = assertThrows(CompletionException.class, () -> second.toCompletableFuture().join());
        assertInstanceOf(CallNotPermittedException.class, secondError.getCause());
    }

    private String app() {
        return """
            @KoraApp
            public interface AppWithConfig extends ConfigValueMapperModule, CircuitBreakerModule {
                default Config config() {
                    return HoconConfigFactory.fromHocon(new SimpleConfigOrigin("test"), ConfigFactory.parseString(
                        \"""
                            resilient {
                              circuitbreaker {
                                custom1 {
                                  slidingWindowSize = 1
                                  minimumRequiredCalls = 1
                                  failureRateThreshold = 100
                                  permittedCallsInHalfOpenState = 1
                                  waitDurationInOpenState = 1s
                                }
                              }
                            }
                            \"""
                    ).resolve());
                }
            }
            """;
    }

    private String appWithRootConfig() {
        return """
            @KoraApp
            public interface AppWithConfig extends ConfigValueMapperModule, CircuitBreakerModule {
                default Config config() {
                    return HoconConfigFactory.fromHocon(new SimpleConfigOrigin("test"), ConfigFactory.parseString(
                        \"""
                            payment {
                              slidingWindowSize = 1
                              minimumRequiredCalls = 1
                              failureRateThreshold = 100
                              permittedCallsInHalfOpenState = 1
                              waitDurationInOpenState = 1s
                            }
                            \"""
                    ).resolve());
                }
            }
            """;
    }
}
