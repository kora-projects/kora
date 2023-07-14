package ru.tinkoff.kora.kora.app.annotation.processor;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DependencyTest extends AbstractKoraAppTest {
    @Test
    public void testSingleDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass1 {}
                class TestClass2 {}

                default TestClass2 testClass2() { return new TestClass2(); }
                @Root
                default TestClass1 typeReference(TypeRef<TestClass2> object) { assert object != null; return new TestClass1(); }
                @Root
                default TestClass1 simpleReference(TestClass2 object) { assert object != null; return new TestClass1(); }
                @Root
                default TestClass1 nullableReference(@Nullable TestClass2 object) { assert object != null; return new TestClass1(); }
                @Root
                default TestClass1 optionalReference(Optional<TestClass2> object) { assert object.isPresent(); return new TestClass1(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(6);
        draw.init().block();
    }

    @Test
    public void testValueOfSingleDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass1 {}
                class TestClass2 {}

                default TestClass2 testClass2() { return new TestClass2(); }
                @Root
                default TestClass1 valueOfReference(ValueOf<TestClass2> object) { assert object != null; return new TestClass1(); }
                @Root
                default TestClass1 valueOfOptionalReference(ValueOf<Optional<TestClass2>> object) { assert object.get().isPresent(); return new TestClass1(); }
                @Root
                default TestClass1 optionalOfValueOfReference(Optional<ValueOf<TestClass2>> object) { assert object.get().get() != null; return new TestClass1(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(6);
        draw.init().block();
    }

    @Test
    public void testPromiseOfSingleDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass1 {}
                class TestClass2 {}

                @Root
                default TestClass2 testClass2() { return new TestClass2(); }
                @Root
                default TestClass1 promiseOfReference(PromiseOf<TestClass2> object) { return new TestClass1(); }
                @Root
                default TestClass1 promiseOfOptionalReference(PromiseOf<Optional<TestClass2>> object) { return new TestClass1(); }
                @Root
                default TestClass1 optionalOfPromiseOfReference(Optional<PromiseOf<TestClass2>> object) { return new TestClass1(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(6);
        draw.init().block();
    }

    @Test
    public void testOptionalDependencies() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass1 {}
                class TestClass2 {}

                @Root
                default TestClass1 nullableReference(@Nullable TestClass2 object) { return new TestClass1(); }
                @Root
                default TestClass1 optionalReference(Optional<TestClass2> object) { return new TestClass1(); }
                @Root
                default TestClass1 valueOfOptionalReference(ValueOf<Optional<TestClass2>> object) { return new TestClass1(); }
                @Root
                default TestClass1 optionalOfValueOfReference(Optional<ValueOf<TestClass2>> object) { return new TestClass1(); }
                @Root
                default TestClass1 promiseOfOptionalReference(PromiseOf<Optional<TestClass2>> object) { return new TestClass1(); }
                @Root
                default TestClass1 optionalOfPromiseOfReference(Optional<PromiseOf<TestClass2>> object) { return new TestClass1(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(9);
        draw.init().block();
    }

    @Test
    public void testAllDependencies() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass1 {}
                interface TestInterface1 {}
                class TestClass2 implements TestInterface1 {}
                class TestClass3 implements TestInterface1 {}
                class TestClass4 implements TestInterface1 {}

                @Root
                default TestClass1 allOfInterface(All<TestInterface1> all) { return new TestClass1(); }
                @Root
                default TestClass1 allOfClass(All<TestClass2> all) { return new TestClass1(); }

                default TestClass2 testClass2() { return new TestClass2(); }
                default TestClass3 testClass3() { return new TestClass3(); }
                default TestClass4 testClass4() { return new TestClass4(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(5);
        draw.init().block();
    }

    @Test
    public void testAllOfValueDependencies() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass1 {}
                interface TestInterface1 {}
                class TestClass2 implements TestInterface1 {}
                class TestClass3 implements TestInterface1 {}
                class TestClass4 implements TestInterface1 {}

                @Root
                default TestClass1 allOfValueOfInterface(All<ValueOf<TestInterface1>> all) { return new TestClass1(); }
                @Root
                default TestClass1 allOfValueOfClass(All<ValueOf<TestClass2>> all) { return new TestClass1(); }

                default TestClass2 testClass2() { return new TestClass2(); }
                default TestClass3 testClass3() { return new TestClass3(); }
                default TestClass4 testClass4() { return new TestClass4(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(5);
        draw.init().block();
    }

    @Test
    public void testAllOfPromiseDependencies() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass1 {}
                interface TestInterface1 {}
                class TestClass2 implements TestInterface1 {}
                class TestClass3 implements TestInterface1 {}
                class TestClass4 implements TestInterface1 {}

                @Root
                default TestClass1 allOfPromiseOfInterface(All<PromiseOf<TestInterface1>> all) { return new TestClass1(); }
                @Root
                default TestClass1 allOfPromiseOfClass(All<PromiseOf<TestClass2>> all) { return new TestClass1(); }


                default TestClass2 testClass2() { return new TestClass2(); }
                default TestClass3 testClass3() { return new TestClass3(); }
                default TestClass4 testClass4() { return new TestClass4(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(5);
        draw.init().block();
    }

    @Test
    public void testEmptyAllDependencies() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                interface TestInterface1 {}
                class TestClass1 {}
                class TestClass2 implements TestInterface1{}

                @Root
                default TestClass1 allOfNothingByClass(All<TestClass2> all) { return new TestClass1(); }
                @Root
                default TestClass1 allOfValueOfNothingByClass(All<ValueOf<TestClass2>> all) { return new TestClass1(); }
                @Root
                default TestClass1 allOfPromiseOfNothingByClass(All<PromiseOf<TestClass2>> all) { return new TestClass1(); }

                @Root
                default TestClass1 allOfNothingByInterface(All<TestInterface1> all) { return new TestClass1(); }
                @Root
                default TestClass1 allOfValueOfNothingByInterface(All<ValueOf<TestInterface1>> all) { return new TestClass1(); }
                @Root
                default TestClass1 allOfPromiseOfNothingByInterface(All<PromiseOf<TestInterface1>> all) { return new TestClass1(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(6);
        draw.init().block();
    }

    @Test
    public void testDiscoveredFinalClassDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                final class TestClass1 {}
                
                @Root
                default String test(TestClass1 testClass) { return ""; }
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        draw.init().block();
    }

    @Test
    public void testDiscoveredFinalClassDependencyWithTag() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Tag(TestClass1.class)
                final class TestClass1 {}
                
                @Root
                default String test(@Tag(TestClass1.class) TestClass1 testClass) { return ""; }
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        draw.init().block();
    }

    @Test
    public void testDiscoveredFinalClassDependencyTaggedDependencyNoTagOnClass() {
        assertThatThrownBy(() -> compile("""
            @KoraApp
            public interface ExampleApplication {
                final class TestClass1 {}
                
                @Root
                default String test(@Tag(TestClass1.class) TestClass1 testClass) { return ""; }
            }
            """));

        assertThat(compileResult.isFailed()).isTrue();
        assertThat(compileResult.errors()).hasSize(1);
        assertThat(compileResult.errors().get(0).getMessage(Locale.ENGLISH))
            .contains("Required dependency was not found: ")
            .contains("@Tag(ru.tinkoff.kora.kora.app.annotation.processor.packageForDependencyTest.testDiscoveredFinalClassDependencyTaggedDependencyNoTagOnClass.ExampleApplication.TestClass1) ")
            .contains("ru.tinkoff.kora.kora.app.annotation.processor.packageForDependencyTest.testDiscoveredFinalClassDependencyTaggedDependencyNoTagOnClass.ExampleApplication.TestClass1")
        ;
    }

    @Test
    public void testRecursiveDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                interface TestInterface1 {}
                interface TestInterface2 {}
                class TestClass2 implements TestInterface1, TestInterface2 {}
                class TestClass1 {}

                default TestInterface1 testInterface1(TestInterface2 p) { return new TestClass2(); }
                default TestInterface2 testInterface2(TestInterface1 p) { return new TestClass2(); }

                @Root
                default TestClass1 root(TestInterface1 testInterface1, TestInterface2 testInterface2) { return new TestClass1(); }
            }
            """);
        Assertions.assertThat(draw.getNodes()).hasSize(4);
        draw.init().block();
    }

}
