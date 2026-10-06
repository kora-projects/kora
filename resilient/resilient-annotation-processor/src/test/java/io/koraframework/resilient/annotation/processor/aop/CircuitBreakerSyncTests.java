package io.koraframework.resilient.annotation.processor.aop;

import io.koraframework.annotation.processor.common.AbstractAnnotationProcessorTest;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.kora.app.annotation.processor.KoraAppProcessor;
import io.koraframework.resilient.annotation.processor.ResilientAnnotationProcessor;
import io.koraframework.resilient.circuitbreaker.CircuitBreaker;
import io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException;
import org.junit.jupiter.api.Test;

import java.util.List;
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
            import io.koraframework.common.annotation.Tag;
            import io.koraframework.resilient.circuitbreaker.CircuitBreakerPredicate;
            import io.koraframework.resilient.ResilientModule;
            import io.koraframework.resilient.circuitbreaker.annotation.CircuitBreakerSpec;
            import io.koraframework.resilient.circuitbreaker.annotation.CircuitBreakable;
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
                @CircuitBreakable(TestCircuitBreaker.class)
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
                @CircuitBreakable(TestCircuitBreaker.class)
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
                @CircuitBreakable(TestCircuitBreaker.class)
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
                @CircuitBreakable(TestCircuitBreaker.class)
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
                @CircuitBreakable(TestCircuitBreaker.class)
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
                @CircuitBreakable(TestCircuitBreaker.class)
                public CompletableFuture<String> getValueFuture() {
                    return CompletableFuture.failedFuture(new IllegalStateException("Failed"));
                }
            }
            """);

        assertFutureCircuitBreaker(service, "getValueFuture");
    }

    @Test
    void sameConfigPathUsesSingleCircuitBreakerComponent() {
        compile(List.of(new KoraAppProcessor(), new ResilientAnnotationProcessor(), new AopAnnotationProcessor()), app(), circuitBreakerInterface(), """
            @Component
            @Root
            public class TestTarget1 {
                @CircuitBreakable(TestCircuitBreaker.class)
                public String getValue() {
                    return "1";
                }
            }
            """, """
            @Component
            @Root
            public class TestTarget2 {
                @CircuitBreakable(TestCircuitBreaker.class)
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
        compile(List.of(new KoraAppProcessor(), new ResilientAnnotationProcessor(), new AopAnnotationProcessor()), appWithRootConfig(), """
            @CircuitBreakerSpec("payment")
            public interface TestCircuitBreaker extends io.koraframework.resilient.circuitbreaker.CircuitBreaker {}
            """, """
            @Component
            @Root
            public class TestTarget {
                @CircuitBreakable(TestCircuitBreaker.class)
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
    void circuitBreakerInterfaceTestIsUsedWhenPredicateIsAbsent() {
        var service = compileApp("""
            @Component
            @Root
            public class TestTarget {
                @CircuitBreakable(TestCircuitBreaker.class)
                public String getValue() {
                    throw new IllegalStateException("Failed");
                }
            }
            """, """
            @CircuitBreakerSpec("resilient.circuitbreaker.custom1")
            public interface TestCircuitBreaker extends io.koraframework.resilient.circuitbreaker.CircuitBreaker {
                @Override
                default boolean isFailure(Throwable throwable) {
                    return false;
                }
            }
            """);

        assertThrows(IllegalStateException.class, () -> invoke(service, "getValue"));
        assertThrows(IllegalStateException.class, () -> invoke(service, "getValue"));
    }

    @Test
    void taggedPredicateOverridesCircuitBreakerInterfaceTest() {
        var service = compileAppWithPredicate("""
            @Component
            @Root
            public class TestTarget {
                @CircuitBreakable(TestCircuitBreaker.class)
                public String getValue() {
                    throw new IllegalStateException("Failed");
                }
            }
            """, """
            @CircuitBreakerSpec("resilient.circuitbreaker.custom1")
            public interface TestCircuitBreaker extends io.koraframework.resilient.circuitbreaker.CircuitBreaker {
                @Override
                default boolean isFailure(Throwable throwable) {
                    return false;
                }
            }
            """);

        assertCircuitBreaker(service, "getValue");
    }

    @Test
    void circuitBreakerInterfaceMustExtendRuntimeCircuitBreaker() {
        compile(List.of(new KoraAppProcessor(), new ResilientAnnotationProcessor(), new AopAnnotationProcessor()), app(), """
            @CircuitBreakerSpec("resilient.circuitbreaker.custom1")
            public interface TestCircuitBreaker {}
            """);

        assertTrue(compileResult.isFailed());
        assertTrue(compileResult.errors().stream()
            .anyMatch(e -> e.getMessage(null).contains("must extend io.koraframework.resilient.circuitbreaker.CircuitBreaker")));
    }

    @Test
    void halfOpenPermitIsReleasedWhenNestedCircuitBreakerRejects() throws Exception {
        compile(List.of(new KoraAppProcessor(), new ResilientAnnotationProcessor(), new AopAnnotationProcessor()), nestedApp(), """
            @CircuitBreakerSpec("resilient.circuitbreaker.outer")
            public interface OuterCircuitBreaker extends io.koraframework.resilient.circuitbreaker.CircuitBreaker {}
            """, """
            @CircuitBreakerSpec("resilient.circuitbreaker.inner")
            public interface InnerCircuitBreaker extends io.koraframework.resilient.circuitbreaker.CircuitBreaker {}
            """, """
            @Component
            public class Client {
                @CircuitBreakable(InnerCircuitBreaker.class)
                public String call() {
                    throw new IllegalStateException("Failed");
                }
            }
            """, """
            @Component
            @Root
            public class TestTarget {
                private final Client client;
                public volatile boolean callClient = true;

                public TestTarget(Client client) {
                    this.client = client;
                }

                @CircuitBreakable(OuterCircuitBreaker.class)
                public String getValue() {
                    return callClient ? client.call() : "OK";
                }
            }
            """);
        compileResult.assertSuccess();
        var service = loadGraph("AppWithConfig").findByType(loadClass("TestTarget"));
        assertNotNull(service);

        // the failing downstream call opens both breakers: the outer for 100ms, the inner for 1h
        assertThrows(IllegalStateException.class, () -> invoke(service, "getValue"));
        Thread.sleep(150);
        // the outer HALF_OPEN probe is rejected by the inner breaker
        assertThrows(CallNotPermittedException.class, () -> invoke(service, "getValue"));
        // the probe permit must have been released, so the outer breaker recovers after waitDurationInOpenState
        service.getClass().getField("callClient").set(service, false);
        Thread.sleep(300);
        assertEquals("OK", invoke(service, "getValue"));
    }

    @Test
    void completionStageCallNotPermittedFromBodyIsReleased() {
        var service = compileApp("""
            @Component
            @Root
            public class TestTarget {
                @CircuitBreakable(TestCircuitBreaker.class)
                public CompletionStage<String> getValueStage() {
                    throw new io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException(
                        io.koraframework.resilient.circuitbreaker.CircuitBreaker.State.OPEN, "nested");
                }
            }
            """);

        assertThrows(CallNotPermittedException.class, () -> invoke(service, "getValueStage"));
        // the body failure was recorded, so the breaker is open and rejects the next call
        var second = assertInstanceOf(CompletionStage.class, invoke(service, "getValueStage"));
        var secondError = assertThrows(CompletionException.class, () -> second.toCompletableFuture().join());
        assertInstanceOf(CallNotPermittedException.class, secondError.getCause());
    }

    private Object compileApp(String target) {
        return compileApp(target, circuitBreakerInterface());
    }

    private Object compileApp(String target, String circuitBreakerInterface) {
        compile(List.of(new KoraAppProcessor(), new ResilientAnnotationProcessor(), new AopAnnotationProcessor()), app(), circuitBreakerInterface, target);
        compileResult.assertSuccess();

        var graph = loadGraph("AppWithConfig");
        var service = graph.findByType(loadClass("TestTarget"));
        assertNotNull(service);
        return service;
    }

    private Object compileAppWithPredicate(String target, String circuitBreakerInterface) {
        compile(List.of(new KoraAppProcessor(), new ResilientAnnotationProcessor(), new AopAnnotationProcessor()), appWithPredicate(), circuitBreakerInterface, target);
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
            public interface AppWithConfig extends ConfigValueMapperModule, ResilientModule {
                default Config config() {
                    return HoconConfigFactory.fromHocon(new SimpleConfigOrigin("test"), ConfigFactory.parseString(
                        \"""
                            resilient {
                              telemetry {
                                circuitBreaker {}
                                retry {}
                                timeout {}
                                fallback {}
                                rateLimiter {}
                              }
                              circuitbreaker {
                                custom1 {
                                  countBased {
                                    windowSize = 1
                                  }
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

    private String nestedApp() {
        return """
            @KoraApp
            public interface AppWithConfig extends ConfigValueMapperModule, ResilientModule {
                default Config config() {
                    return HoconConfigFactory.fromHocon(new SimpleConfigOrigin("test"), ConfigFactory.parseString(
                        \"""
                            resilient {
                              telemetry {
                                circuitBreaker {}
                                retry {}
                                timeout {}
                                fallback {}
                                rateLimiter {}
                              }
                              circuitbreaker {
                                outer {
                                  countBased.windowSize = 1
                                  minimumRequiredCalls = 1
                                  failureRateThreshold = 100
                                  permittedCallsInHalfOpenState = 1
                                  waitDurationInOpenState = 100ms
                                }
                                inner {
                                  countBased.windowSize = 1
                                  minimumRequiredCalls = 1
                                  failureRateThreshold = 100
                                  permittedCallsInHalfOpenState = 1
                                  waitDurationInOpenState = 1h
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
            public interface AppWithConfig extends ConfigValueMapperModule, ResilientModule {
                default Config config() {
                    return HoconConfigFactory.fromHocon(new SimpleConfigOrigin("test"), ConfigFactory.parseString(
                        \"""
                            payment {
                              countBased {
                                windowSize = 1
                              }
                              minimumRequiredCalls = 1
                              failureRateThreshold = 100
                              permittedCallsInHalfOpenState = 1
                              waitDurationInOpenState = 1s
                            }
                            resilient {
                              telemetry {
                                circuitBreaker {}
                                retry {}
                                timeout {}
                                fallback {}
                                rateLimiter {}
                              }
                            }
                            \"""
                    ).resolve());
                }
            }
            """;
    }

    private String appWithPredicate() {
        return """
            @KoraApp
            public interface AppWithConfig extends ConfigValueMapperModule, ResilientModule {
                @Tag(TestCircuitBreaker.class)
                default CircuitBreakerPredicate testCircuitBreakerPredicate() {
                    return throwable -> true;
                }

                default Config config() {
                    return HoconConfigFactory.fromHocon(new SimpleConfigOrigin("test"), ConfigFactory.parseString(
                        \"""
                            resilient {
                              telemetry {
                                circuitBreaker {}
                                retry {}
                                timeout {}
                                fallback {}
                                rateLimiter {}
                              }
                              circuitbreaker {
                                custom1 {
                                  countBased {
                                    windowSize = 1
                                  }
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

    private String circuitBreakerInterface() {
        return """
            @CircuitBreakerSpec("resilient.circuitbreaker.custom1")
            public interface TestCircuitBreaker extends io.koraframework.resilient.circuitbreaker.CircuitBreaker {}
            """;
    }
}
