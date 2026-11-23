package io.koraframework.kora.app.annotation.processor;

import io.koraframework.annotation.processor.common.CompileResult;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.application.graph.RefreshableGraph;
import io.koraframework.application.graph.internal.NodeImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GraphInterceptorTest extends AbstractKoraAppTest {

    @Test
    public void testGraphInterceptor() {
        var draw = compile("""
            import io.koraframework.application.graph.GraphInterceptor;

            @KoraApp
            public interface ExampleApplication {
                class TestRoot {}
                class TestClass {}
                class TestInterceptor implements GraphInterceptor<TestClass> {
                    public TestClass afterInit(TestClass value) {
                        return value;
                    }

                    public TestClass beforeRelease(TestClass value) {
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
        assertThat(((NodeImpl<?>) draw.getNodes().get(1)).interceptors).hasSize(1);
    }

    @Test
    public void testGraphInterceptorForAopParent() throws Exception {
        var draw = compileWithAop(
            """
                import io.koraframework.annotation.processor.common.TestAspect;
                import io.koraframework.application.graph.GraphInterceptor;

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
                        public TestClass afterInit(TestClass value) {
                            return value;
                        }

                        public TestClass beforeRelease(TestClass value) {
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
        assertThat((node).interceptors).hasSize(1);
        var value = node.factory.get(init);
        assertThat(value.getClass().getSimpleName()).isEqualTo("$ExampleApplication_TestClass__AopProxy");
    }

    @Test
    public void testComponentWithAspectOnPackagePrivateMethodResolvesToAopProxy() throws Exception {
        var draw = compileWithAop(
            """
                import io.koraframework.annotation.processor.common.TestAspect;

                @KoraApp
                public interface ExampleApplication {
                    class TestRoot {}

                    @Component
                    class TestClass {
                        @TestAspect
                        String getSome() {
                            return "1";
                        }
                    }

                    @Root
                    default TestRoot root(TestClass testClass) {
                        return new TestRoot();
                    }
                }
                """);
        assertThat(draw.getNodes()).hasSize(2);
        var init = draw.init();
        var value = ((NodeImpl<?>) draw.getNodes().get(0)).factory.get(init);
        assertThat(value.getClass().getSimpleName()).isEqualTo("$ExampleApplication_TestClass__AopProxy");
    }

    @Test
    public void testComponentDeclaredAsAopProxyFails() {
        assertThatThrownBy(() -> compileWithAop(
            """
                import io.koraframework.annotation.processor.common.TestAspect;

                @KoraApp
                public interface ExampleApplication {
                    class TestRoot {}

                    class TestClass {
                        @TestAspect
                        public String getSome() {
                            return "1";
                        }
                    }

                    default $ExampleApplication_TestClass__AopProxy testClass() {
                        return new $ExampleApplication_TestClass__AopProxy();
                    }

                    @Root
                    default TestRoot root($ExampleApplication_TestClass__AopProxy testClass) {
                        return new TestRoot();
                    }
                }
                """))
            .isInstanceOf(CompileResult.CompilationFailedException.class)
            .hasMessageContaining("Component provider returns a generated AOP proxy type")
            .hasMessageContaining("Declare the return type as the original type: ")
            .hasMessageContaining(".testComponentDeclaredAsAopProxyFails.ExampleApplication.TestClass");
    }

    @Test
    public void testSubmoduleDeclaresAopProxyComponentWithOriginalType() throws Exception {
        var compileResult = compile(List.of(new AopAnnotationProcessor(), new KoraAppProcessor(), new KoraSubmoduleProcessor()),
            """
                import io.koraframework.annotation.processor.common.TestAspect;

                @io.koraframework.common.annotation.KoraSubmodule
                public interface ExampleSubmodule {
                    @Component
                    class TestClass {
                        @TestAspect
                        public String getSome() {
                            return "1";
                        }
                    }
                }
                """,
            """
                @KoraApp
                public interface ExampleApplication {
                    class TestRoot {}

                    @Root
                    default TestRoot root(ExampleSubmodule.TestClass testClass) {
                        return new TestRoot();
                    }
                }
                """);
        compileResult.assertSuccess();

        var submodule = compileResult.loadClass("ExampleSubmoduleSubmoduleImpl");
        var testClass = compileResult.loadClass("ExampleSubmodule$TestClass");
        var factory = submodule.getMethod("_component0");
        assertThat(factory.getReturnType()).isEqualTo(testClass);
        var instance = java.lang.reflect.Proxy.newProxyInstance(submodule.getClassLoader(), new Class<?>[]{submodule}, (proxy, method, args) -> java.lang.reflect.InvocationHandler.invokeDefault(proxy, method, args));
        assertThat(factory.invoke(instance).getClass().getSimpleName()).isEqualTo("$ExampleSubmodule_TestClass__AopProxy");
    }

    @Test
    public void testGraphInterceptorForRoot() {
        var draw = compile("""
            import io.koraframework.application.graph.GraphInterceptor;

            @KoraApp
            public interface ExampleApplication {
                class TestRoot {}
                class TestInterceptor implements GraphInterceptor<TestRoot> {
                    public TestRoot afterInit(TestRoot value) {
                        return value;
                    }

                    public TestRoot beforeRelease(TestRoot value) {
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
        assertThat(((NodeImpl<?>) draw.getNodes().get(1)).interceptors).hasSize(1);
    }

}
