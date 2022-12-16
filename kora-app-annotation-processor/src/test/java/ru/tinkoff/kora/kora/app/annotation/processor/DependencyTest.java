package ru.tinkoff.kora.kora.app.annotation.processor;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class DependencyTest extends AbstractKoraAppTest {
    @Test
    public void testSingleDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass1 implements MockLifecycle {}
                class TestClass2 {}

                default TestClass2 testClass2() { return new TestClass2(); }
                default TestClass1 typeReference(TypeRef<TestClass2> object) { assert object != null; return new TestClass1(); }
                default TestClass1 simpleReference(TestClass2 object) { assert object != null; return new TestClass1(); }
                default TestClass1 nullableReference(@Nullable TestClass2 object) { assert object != null; return new TestClass1(); }
                default TestClass1 optionalReference(Optional<TestClass2> object) { assert object.isPresent(); return new TestClass1(); }
            }
            """);
        Assertions.assertThat(draw.getNodes()).hasSize(6);
        draw.init().block();
    }

    @Test
    public void testValueOfSingleDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass1 implements MockLifecycle {}
                class TestClass2 {}

                default TestClass2 testClass2() { return new TestClass2(); }
                default TestClass1 valueOfReference(ValueOf<TestClass2> object) { assert object != null; return new TestClass1(); }
                default TestClass1 valueOfOptionalReference(ValueOf<Optional<TestClass2>> object) { assert object.get().isPresent(); return new TestClass1(); }
                default TestClass1 optionalOfValueOfReference(Optional<ValueOf<TestClass2>> object) { assert object.get().get() != null; return new TestClass1(); }
            }
            """);
        Assertions.assertThat(draw.getNodes()).hasSize(6);
        draw.init().block();
    }

    @Test
    public void testPromiseOfSingleDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass1 implements MockLifecycle {}
                class TestClass2 {}

                default TestClass2 testClass2() { return new TestClass2(); }
                default TestClass1 promiseOfReference(PromiseOf<TestClass2> object) { return new TestClass1(); }
                default TestClass1 promiseOfOptionalReference(PromiseOf<Optional<TestClass2>> object) { return new TestClass1(); }
                default TestClass1 optionalOfPromiseOfReference(Optional<PromiseOf<TestClass2>> object) { return new TestClass1(); }
            }
            """);
        Assertions.assertThat(draw.getNodes()).hasSize(6);
        draw.init().block();
    }

    @Test
    public void testOptionalDependencies() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass1 implements MockLifecycle {}
                class TestClass2 {}

                default TestClass1 nullableReference(@Nullable TestClass2 object) { return new TestClass1(); }
                default TestClass1 optionalReference(Optional<TestClass2> object) { return new TestClass1(); }
                default TestClass1 valueOfOptionalReference(ValueOf<Optional<TestClass2>> object) { return new TestClass1(); }
                default TestClass1 optionalOfValueOfReference(Optional<ValueOf<TestClass2>> object) { return new TestClass1(); }
                default TestClass1 promiseOfOptionalReference(PromiseOf<Optional<TestClass2>> object) { return new TestClass1(); }
                default TestClass1 optionalOfPromiseOfReference(Optional<PromiseOf<TestClass2>> object) { return new TestClass1(); }
            }
            """);
        Assertions.assertThat(draw.getNodes()).hasSize(9);
        draw.init().block();
    }

    @Test
    public void testAllDependencies() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass1 implements MockLifecycle {}
                interface TestInterface1 {}
                class TestClass2 implements TestInterface1 {}
                class TestClass3 implements TestInterface1 {}
                class TestClass4 implements TestInterface1 {}

                default TestClass1 allOfInterface(All<TestInterface1> all) { return new TestClass1(); }
                default TestClass1 allOfClass(All<TestClass2> all) { return new TestClass1(); }

                default TestClass2 testClass2() { return new TestClass2(); }
                default TestClass3 testClass3() { return new TestClass3(); }
                default TestClass4 testClass4() { return new TestClass4(); }
            }
            """);
        Assertions.assertThat(draw.getNodes()).hasSize(5);
        draw.init().block();
    }

    @Test
    public void testAllOfValueDependencies() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass1 implements MockLifecycle {}
                interface TestInterface1 {}
                class TestClass2 implements TestInterface1 {}
                class TestClass3 implements TestInterface1 {}
                class TestClass4 implements TestInterface1 {}

                default TestClass1 allOfValueOfInterface(All<ValueOf<TestInterface1>> all) { return new TestClass1(); }
                default TestClass1 allOfValueOfClass(All<ValueOf<TestClass2>> all) { return new TestClass1(); }

                default TestClass2 testClass2() { return new TestClass2(); }
                default TestClass3 testClass3() { return new TestClass3(); }
                default TestClass4 testClass4() { return new TestClass4(); }
            }
            """);
        Assertions.assertThat(draw.getNodes()).hasSize(5);
        draw.init().block();
    }

    @Test
    public void testAllOfPromiseDependencies() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass1 implements MockLifecycle {}
                interface TestInterface1 {}
                class TestClass2 implements TestInterface1 {}
                class TestClass3 implements TestInterface1 {}
                class TestClass4 implements TestInterface1 {}

                default TestClass1 allOfPromiseOfInterface(All<PromiseOf<TestInterface1>> all) { return new TestClass1(); }
                default TestClass1 allOfPromiseOfClass(All<PromiseOf<TestClass2>> all) { return new TestClass1(); }


                default TestClass2 testClass2() { return new TestClass2(); }
                default TestClass3 testClass3() { return new TestClass3(); }
                default TestClass4 testClass4() { return new TestClass4(); }
            }
            """);
        Assertions.assertThat(draw.getNodes()).hasSize(5);
        draw.init().block();
    }

    @Test
    public void testEmptyAllDependencies() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                interface TestInterface1 {}
                class TestClass1 implements MockLifecycle {}
                class TestClass2 implements TestInterface1{}


                default TestClass1 allOfNothingByClass(All<TestClass2> all) { return new TestClass1(); }
                default TestClass1 allOfValueOfNothingByClass(All<ValueOf<TestClass2>> all) { return new TestClass1(); }
                default TestClass1 allOfPromiseOfNothingByClass(All<PromiseOf<TestClass2>> all) { return new TestClass1(); }

                default TestClass1 allOfNothingByInterface(All<TestInterface1> all) { return new TestClass1(); }
                default TestClass1 allOfValueOfNothingByInterface(All<ValueOf<TestInterface1>> all) { return new TestClass1(); }
                default TestClass1 allOfPromiseOfNothingByInterface(All<PromiseOf<TestInterface1>> all) { return new TestClass1(); }
            }
            """);
        Assertions.assertThat(draw.getNodes()).hasSize(6);
        draw.init().block();
    }
}
