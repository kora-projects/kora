package io.koraframework.kora.app.annotation.processor;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

class ComponentTest extends AbstractKoraAppTest {
    @Test
    public void testComponentAnnotatedClass() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestClass object) { return java.util.Objects.requireNonNull(object); }
            }
            """, """
            @Component
            public class TestClass {
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        draw.init();
    }

    @Test
    public void testComponentAnnotatedAbstractClass() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestClass object, TestInterface testInterface) { return java.util.Objects.requireNonNull(object); }
            }
            """, """
            @Component
            public abstract class TestClass {
            }
            """, """
            @Component
            public interface TestInterface {
            }
            """, """
            @Component
            public class TestClass1 extends TestClass {
            }
            """, """
            @Component
            public class TestClass2 implements TestInterface {
            }
            """);
        assertThat(draw.getNodes()).hasSize(3);
        draw.init();
    }

    @Test
    public void testReleaseReportsEveryFailingComponent() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                final class A implements Lifecycle {
                    public void init() {}
                    public void release() { throw new IllegalStateException("release A failed"); }
                }
                final class B implements Lifecycle {
                    public void init() {}
                    public void release() { throw new IllegalStateException("release B failed"); }
                }
                @Root
                default A a() { return new A(); }
                @Root
                default B b() { return new B(); }
            }
            """);
        var graph = draw.init();
        var error = catchThrowable(graph::release);
        assertThat(error).hasMessageContaining("failed to release with 2 errors");
        assertThat(Arrays.stream(error.getSuppressed()).map(Throwable::getMessage))
            .containsExactlyInAnyOrder("release A failed", "release B failed");
    }

    @Test
    public void testNullFromFactoryNamesTheComponent() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                final class Foo {}
                default Foo foo() { return null; }
                final class R {}
                @Root
                default R r(Foo foo) { return new R(); }
            }
            """);
        var error = catchThrowable(draw::init);
        assertThat(error)
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("ExampleApplication$Foo");
    }
}
