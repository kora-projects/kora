package io.koraframework.kora.app.annotation.processor;

import io.koraframework.annotation.processor.common.CompileResult;
import io.koraframework.aop.annotation.processor.AopAnnotationProcessor;
import io.koraframework.application.graph.RefreshableGraph;
import io.koraframework.application.graph.internal.NodeImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GraphInterceptorTest extends AbstractKoraAppTest {
    public static final List<String> EVENTS = new CopyOnWriteArrayList<>();
    public static final AtomicInteger SEQ = new AtomicInteger();

    @BeforeEach
    void reset() {
        EVENTS.clear();
        SEQ.set(0);
    }

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

    @Test
    public void testInterceptorWithFailedConditionIsSkipped() throws Exception {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                final class OffTag {}
                @Tag(OffTag.class)
                default GraphCondition off() { return () -> GraphCondition.ConditionResult.failed("off"); }
                final class X {}
                final class Icp implements GraphInterceptor<X> {
                    public X afterInit(X x) { %1$s.EVENTS.add("afterInit"); return x; }
                    public X beforeRelease(X x) { %1$s.EVENTS.add("beforeRelease"); return x; }
                }
                @Conditional(tag = OffTag.class)
                default Icp icp() { return new Icp(); }
                default X x() { return new X(); }
                final class R { public R(X x) {} }
                @Root
                default R r(X x) { return new R(x); }
            }
            """.formatted(GraphInterceptorTest.class.getCanonicalName()));
        var graph = draw.init();
        graph.release();
        assertThat(EVENTS).isEmpty();
    }

    @Test
    public void testRefreshedInterceptorInterceptsEqualComponent() throws Exception {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                final class Cfg {}
                record X(String v) {}
                final class Icp implements GraphInterceptor<X> {
                    final int id = %1$s.SEQ.incrementAndGet();
                    public X afterInit(X x) { %1$s.EVENTS.add("afterInit icp" + id); return x; }
                    public X beforeRelease(X x) { %1$s.EVENTS.add("beforeRelease icp" + id); return x; }
                }
                default Cfg cfg() { return new Cfg(); }
                default Icp icp(Cfg cfg) { return new Icp(); }
                default X x() { return new X("x"); }
                final class R { public R(X x) {} }
                @Root
                default R r(X x) { return new R(x); }
            }
            """.formatted(GraphInterceptorTest.class.getCanonicalName()));
        var graph = draw.init();
        var cfg = draw.getNodes().stream().filter(n -> n.type().getTypeName().endsWith("$Cfg")).findFirst().orElseThrow();
        graph.refresh(cfg);
        graph.release();
        assertThat(EVENTS).containsExactly("afterInit icp1", "afterInit icp2", "beforeRelease icp1", "beforeRelease icp2");
    }

}
