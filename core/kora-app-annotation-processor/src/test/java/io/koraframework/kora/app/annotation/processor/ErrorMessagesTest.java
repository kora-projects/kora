package io.koraframework.kora.app.annotation.processor;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class ErrorMessagesTest extends AbstractKoraAppTest {

    @Test
    public void multipleGraphConditionsListCandidates() {
        var message = errorMessage("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                @Conditional(tag = Cond1.class)
                default Object root() { return ""; }

                @Tag(Cond1.class)
                default GraphCondition cond1() { return new Cond1(); }

                @Tag(Cond1.class)
                default GraphCondition cond2() { return new Cond1(); }
            }
            """, """
            public class Cond1 implements GraphCondition {
                @Override
                public ConditionResult eval() { return new ConditionResult.Matched("cond1"); }
            }
            """);

        assertThat(message).isEqualTo("""
            Multiple GraphCondition components match condition tag:
              required condition tag: @Tag(%1$s.Cond1.class)
              component: factory  %1$s.ExampleApplication#root()

            Candidates:
              - factory  %1$s.ExampleApplication#cond1()
              - factory  %1$s.ExampleApplication#cond2()

            Fix:
              - Keep only one GraphCondition for this tag.
              - Use different @Tag(...) values for different conditions.""".formatted(testPackage()));
    }

    @Test
    public void cycleThroughAllHasNote() {
        var message = errorMessage("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestInterface o) { return o; }

                @Tag(Cond1.class)
                default GraphCondition cond1(All<TestInterface> all) { return new Cond1(); }
            }
            """, """
            public interface TestInterface {}
            """, """
            public class Cond1 implements GraphCondition {
                @Override
                public ConditionResult eval() { return new ConditionResult.Matched("cond1"); }
            }
            """, """
            @Component
            @Conditional(tag = Cond1.class)
            public class TestClass1 implements TestInterface {}
            """);

        assertThat(message)
            .contains("Dependency cycle:")
            .contains("[CYCLE]")
            .contains("parameter: io.koraframework.application.graph.All<" + testPackage() + ".TestInterface> all")
            .contains("Cycle goes through All<T>, TypeRef<T> or Graph dependency, which cannot be replaced with a proxy.");
    }

    @Test
    public void componentWithSeveralConstructorsListsThem() {
        var message = errorMessage("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestClass1 o) { return o; }
            }
            """, """
            @Component
            public class TestClass1 {
                public TestClass1() {}
                public TestClass1(String value) {}
            }
            """);

        assertThat(message).isEqualTo("""
            @Component class must have exactly one public constructor:
              class: %1$s.TestClass1
              found: 2 public constructors:
                - %1$s.TestClass1()
                - %1$s.TestClass1(String)

            Fix:
              - Keep one public constructor.
              - Make extra constructors non-public.
              - Move complex construction logic to a module method.""".formatted(testPackage()));
    }

    @Test
    public void rawTypeDependencyShowsRequiredAt() {
        var message = errorMessage("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                @SuppressWarnings("rawtypes")
                default Object root(java.util.List list) { return list; }
            }
            """);

        assertThat(message).isEqualTo("""
            Dependency uses a raw type:
              type: java.util.List

            Required at:
              %1$s.ExampleApplication#root(
                java.util.List)
              parameter: java.util.List list

            Raw types are forbidden because they make dependency resolution ambiguous.

            Fix:
              - Specify generic type arguments explicitly.
              - Replace raw collections/providers with parameterized types.""".formatted(testPackage()));
    }

    @Test
    public void untaggedDependencySuggestsTagOfFoundComponent() {
        var message = errorMessage("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(TestService service) { return service; }

                @MyTag
                default TestService service() { return new TestService(); }
            }
            """, """
            public class TestService {}
            """, """
            @Tag(MyTag.class)
            @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
            public @interface MyTag {}
            """);

        assertThat(message)
            .startsWith("""
                No component found for dependency:
                  %1$s.TestService (no tags)""".formatted(testPackage()))
            .contains("""
                Note:
                  Found component(s) of the same type with other tags. Maybe the tag was forgotten or mixed up:
                  - %1$s.TestService with @%1$s.MyTag from factory  %1$s.ExampleApplication#service()

                Fix:
                  - Request the dependency with @%1$s.MyTag to use the component with this tag.
                  - Or remove the tag from the component declaration so it matches this dependency.
                  - Add @Component to an implementation of %1$s.TestService.""".formatted(testPackage()));
    }

    @Test
    public void taggedDependencySuggestsRemovingTag() {
        var message = errorMessage("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(@Tag(OtherTag.class) TestService service) { return service; }

                default TestService service() { return new TestService(); }
            }
            """, """
            public class TestService {}
            """, """
            public final class OtherTag {}
            """);

        assertThat(message)
            .startsWith("""
                No component found for dependency:
                  %1$s.TestService with @Tag(%1$s.OtherTag.class)""".formatted(testPackage()))
            .contains("^--- %1$s.TestService @Tag(%1$s.OtherTag.class) [MISSING]".formatted(testPackage()))
            .contains("""
                  - %1$s.TestService (no tags) from factory  %1$s.ExampleApplication#service()

                Fix:
                  - Remove @Tag(%1$s.OtherTag.class) from the dependency to use the component without tags.
                  - Or add @Tag(%1$s.OtherTag.class) to the component declaration so it matches this dependency.""".formatted(testPackage()));
    }

    @Test
    public void nonStaticNestedComponent() {
        var message = errorMessage("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(Outer.Inner o) { return o; }
            }
            """, """
            public class Outer {
                @Component
                public class Inner {}
            }
            """);

        assertThat(message).isEqualTo("""
            @Component nested class must be static:
              class: %1$s.Outer.Inner

            Fix:
              - Make the nested class static.
              - Move the class to the top level.""".formatted(testPackage()));
    }

    @Test
    public void moduleWithAbstractMethod() {
        var message = errorMessage("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(Long l) { return l; }
            }
            """, """
            @Module
            public interface TestModule {
                default Long l() { return 1L; }
                String name();
            }
            """);

        assertThat(message).isEqualTo("""
            @Module method must be a default method:
              method: %1$s.TestModule#name()

            Fix:
              - Add a default implementation.
              - Remove the method from the module.""".formatted(testPackage()));
    }

    @Test
    public void genericModule() {
        var message = errorMessage("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(java.util.List<String> l) { return l; }
            }
            """, """
            @Module
            public interface TestModule<T> {
                default java.util.List<T> l() { return java.util.List.of(); }
            }
            """);

        assertThat(message).isEqualTo("""
            @Module interface cannot declare type parameters:
              module: %1$s.TestModule

            Fix:
              - Remove type parameters from the module.
              - Declare generic factory methods instead: default <T> List<T> list() {...}""".formatted(testPackage()));
    }

    private String errorMessage(String... sources) {
        assertThat(catchThrowable(() -> compile(sources))).isNotNull();
        var message = compileResult.errors().getFirst().getMessage(Locale.US);
        // javac indents all lines of multiline diagnostic except the first one
        return message.replace("\n  ", "\n");
    }
}
