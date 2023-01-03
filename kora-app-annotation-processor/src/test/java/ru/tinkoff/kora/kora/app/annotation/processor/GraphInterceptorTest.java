package ru.tinkoff.kora.kora.app.annotation.processor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GraphInterceptorTest extends AbstractKoraAppTest {
    @Test
    public void testGraphInterceptor() {
        var draw = compile("""
            import ru.tinkoff.kora.application.graph.GraphInterceptor;
            import reactor.core.publisher.Mono;
                        
            @KoraApp
            public interface ExampleApplication {
                class TestRoot implements MockLifecycle {}
                class TestClass {}
                class TestInterceptor implements GraphInterceptor<TestClass> {
                    public Mono<TestClass> init(TestClass value) {
                        return Mono.just(value);
                    }
                
                    public Mono<TestClass> release(TestClass value) {
                        return Mono.just(value);
                    }
                }

                default TestRoot root(TestClass testClass) {
                    return new TestRoot();
                }
                
                default TestInterceptor interceptor() {
                    return new TestInterceptor();
                }
                
                default TestClass testClass() {
                    return new TestClass();
                }
            }
            """);
        assertThat(draw.getNodes()).hasSize(3);
        draw.init().block();
        assertThat(draw.getNodes().get(1).getInterceptors()).hasSize(1);
    }

    @Test
    public void testGraphInterceptorForRoot() {
        var draw = compile("""
            import ru.tinkoff.kora.application.graph.GraphInterceptor;
            import reactor.core.publisher.Mono;
                        
            @KoraApp
            public interface ExampleApplication {
                class TestRoot implements MockLifecycle {}
                class TestInterceptor implements GraphInterceptor<TestRoot> {
                    public Mono<TestRoot> init(TestRoot value) {
                        return Mono.just(value);
                    }
                
                    public Mono<TestRoot> release(TestRoot value) {
                        return Mono.just(value);
                    }
                }

                default TestRoot root() {
                    return new TestRoot();
                }
                
                default TestInterceptor interceptor() {
                    return new TestInterceptor();
                }                
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        draw.init().block();
        assertThat(draw.getNodes().get(1).getInterceptors()).hasSize(1);
    }

}
