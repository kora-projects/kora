package ru.tinkoff.kora.kora.app.annotation.processor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class ComponentTemplatesTest extends AbstractKoraAppTest {
    @Test
    public void testComponentAnnotatedClass() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                default String dependency() { return ""; }
                @Root
                default Object root(TestClass<String> object) { return java.util.Objects.requireNonNull(object); }
            }
            """, """
            @Component
            public class TestClass<T> {
                public TestClass(T object){}
            }
            """);
        assertThat(draw.getNodes()).hasSize(3);
        draw.init();
    }

}
