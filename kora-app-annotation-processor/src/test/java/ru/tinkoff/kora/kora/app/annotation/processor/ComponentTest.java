package ru.tinkoff.kora.kora.app.annotation.processor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
        draw.init().block();
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
        draw.init().block();
    }
}
