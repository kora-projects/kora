package ru.tinkoff.kora.kora.app.annotation.processor;

import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.application.graph.RefreshableGraph;
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
    public void testGraphInterceptorForAopParent() throws Exception {
        var draw = compileWithAop(
            """
                import ru.tinkoff.kora.annotation.processor.common.TestAspect;
                import ru.tinkoff.kora.application.graph.GraphInterceptor;

                @KoraApp
                public interface ExampleApplication {
                
                    class TestRoot {}
                
                    @Component
                    class TestClass {
                
                        @TestAspect
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
                }
                """);
        assertThat(draw.getNodes()).hasSize(3);
        RefreshableGraph init = draw.init();

        NodeImpl<?> node = (NodeImpl<?>) draw.getNodes().get(1);
        assertThat((node).getInterceptors()).hasSize(1);
        var value = node.factory.get(init);
        assertThat(value.getClass().getSimpleName()).isEqualTo("$ExampleApplication_TestClass__AopProxy");
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
