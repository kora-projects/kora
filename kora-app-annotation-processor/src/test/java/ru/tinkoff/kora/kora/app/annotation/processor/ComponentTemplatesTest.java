package ru.tinkoff.kora.kora.app.annotation.processor;

import org.assertj.core.api.Assert;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import ru.tinkoff.kora.annotation.processor.common.CompileResult;

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

    @Test
    public void testInnerTypeParamsMatchCorrectly() {
        try {
            compile("""
                import java.util.*;
                @KoraApp
                public interface ExampleApplication {
                    default <T> MyJsonWriter<List<T>> dependency1() {return new MyJsonWriter<>();}
                
                    class MyJsonWriter<T>{}
                
                    @Root
                    default Object root(MyJsonWriter<Collection<String>> object) { return java.util.Objects.requireNonNull(object); }
                }
                """);
            Assertions.fail("Should throw an exception");
        } catch (CompileResult.CompilationFailedException e) {
            Assertions.assertThat(e)
                .hasMessageContaining("Required dependency type wasn't found");
        }
    }

    @Test
    public void testOuterTypeParamsMatchCorrectly() {
        compile("""
            import java.util.*;
            @KoraApp
            public interface ExampleApplication {
                default <T> MyJsonWriterImpl<List<T>> dependency1() {return new MyJsonWriterImpl<>();}
            
                interface MyJsonWriter<T>{}
                class MyJsonWriterImpl<T> implements MyJsonWriter<T>{}
            
                @Root
                default Object root(MyJsonWriter<List<String>> object) { return java.util.Objects.requireNonNull(object); }
            }
            """);
    }
}
