package io.koraframework.kora.app.annotation.processor;

import io.koraframework.annotation.processor.common.CompileResult;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Locale;

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
                .hasMessageContaining("No component found for dependency:");
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

    @Test
    public void testUnresolvedDependencyAfterMultipleTemplatesReportsActuallyMissingDependency() {
        Assertions.assertThat(Assertions.catchThrowable(() -> compile("""
            @KoraApp
            public interface ExampleApplication {
                class Wrapper<T> {}
                class Unresolvable<T> {}
                class Missing {}

                default <T> Wrapper<T> wrapper1(Unresolvable<T> unresolvable) { return new Wrapper<>(); }
                default <T> Wrapper<T> wrapper2() { return new Wrapper<>(); }

                @Root
                default Object root(Wrapper<String> wrapper, Missing missing) { return wrapper; }
            }
            """))).isNotNull();
        Assertions.assertThat(compileResult.errors().getFirst().getMessage(Locale.US))
            .contains("No component found for dependency:")
            .contains("ExampleApplication.Missing")
            .doesNotContain("ExampleApplication.Unresolvable");
    }
}
