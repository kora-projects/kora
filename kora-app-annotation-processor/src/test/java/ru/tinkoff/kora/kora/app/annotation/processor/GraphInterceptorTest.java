package ru.tinkoff.kora.kora.app.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.application.graph.internal.NodeImpl;

import static org.assertj.core.api.Assertions.assertThat;

public class GraphInterceptorTest extends AbstractKoraAppTest {

    @Test
    public void testGraphInterceptor() {
        var draw = compile("""
            import ru.tinkoff.kora.application.graph.GraphInterceptor;

            @KoraApp
            public interface ExampleApplication {
                class TestRoot {}
                class TestClass {}
                class TestInterceptor implements GraphInterceptor<TestClass> {
                    public TestClass init(TestClass value) {
                        return value;
                    }

                    public TestClass release(TestClass value) {
                        return value;
                    }
                }

                default TestClass testClass() {
                    return new TestClass();
                }

                @Root
                default TestRoot root(TestClass testClass) {
                    return new TestRoot();
                }

                default TestInterceptor interceptor() {
                    return new TestInterceptor();
                }
            }
            """);
        assertThat(draw.getNodes()).hasSize(3);
        draw.init();
        assertThat(((NodeImpl<?>) draw.getNodes().get(1)).getInterceptors()).hasSize(1);
    }

    @Test
    public void testGraphInterceptorForAopParent() {
        var draw = compileWithAop(
            """
                import ch.qos.logback.classic.LoggerContext;
                import org.slf4j.ILoggerFactory;
                import ru.tinkoff.kora.logging.common.annotation.Log;
                import ru.tinkoff.kora.application.graph.GraphInterceptor;

                @KoraApp
                public interface ExampleApplication {
                            
                    class TestRoot {}
                    
                    @Component
                    class TestClass {
                               
                        @Log
                        public String getSome() {
                            return "1";
                        }
                    }
                    
                    class TestInterceptor implements GraphInterceptor<TestClass> {
                        public TestClass init(TestClass value) {
                            return value;
                        }

                        public TestClass release(TestClass value) {
                            return value;
                        }
                    }

                    @Root
                    default TestRoot root(TestClass testClass) {
                        return new TestRoot();
                    }
                    
                    default TestInterceptor interceptor() {
                        return new TestInterceptor();
                    }
                    
                    default ILoggerFactory someLoggerFactory() {
                        return new LoggerContext();
                    }
                }
                """);
        assertThat(draw.getNodes()).hasSize(4);
        draw.init();
        assertThat(((NodeImpl<?>) draw.getNodes().get(2)).getInterceptors()).hasSize(1);
    }

    @Test
    public void testGraphInterceptorForRoot() {
        var draw = compile("""
            import ru.tinkoff.kora.application.graph.GraphInterceptor;

            @KoraApp
            public interface ExampleApplication {
                class TestRoot {}
                class TestInterceptor implements GraphInterceptor<TestRoot> {
                    public TestRoot init(TestRoot value) {
                        return value;
                    }

                    public TestRoot release(TestRoot value) {
                        return value;
                    }
                }

                @Root
                default TestRoot root() {
                    return new TestRoot();
                }

                default TestInterceptor interceptor() {
                    return new TestInterceptor();
                }
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        draw.init();
        assertThat(((NodeImpl<?>) draw.getNodes().get(1)).getInterceptors()).hasSize(1);
    }

}
