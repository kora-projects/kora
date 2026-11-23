package io.koraframework.kora.app.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.application.graph.PromiseOf;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.annotation.Module;
import io.koraframework.common.annotation.Tag;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

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
        draw.init();
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
        draw.init();
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
        draw.init();
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
        draw.init();
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
        draw.init();
    }

    @Test
    public void testAllWithOnlyDefault() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                interface TestInterface {}
                class TestClass implements TestInterface {}
            
                @Root
                default Object root(All<TestInterface> all) { return ""; }
            
                @DefaultComponent
                default TestClass defaultDependency() { return new TestClass(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        draw.init();
    }

    @Test
    public void testAllWithDefaultAndNonDefault() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                interface TestInterface {}
                class TestClass1 implements TestInterface {}
                class TestClass2 implements TestInterface {}
            
                @Root
                default Object root1(All<TestInterface> all) { return ""; }
            
                @DefaultComponent
                default TestClass1 defaultDependency() { return new TestClass1(); }
            
                default TestClass2 nonDefaultDependency() { return new TestClass2(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        draw.init();
    }

    @Test
    @Disabled
    public void testBugged() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                interface TestInterface {}
                class TestClass1 implements TestInterface {}
                class TestClass2 implements TestInterface {}
            
                @Root
                default Object root2(TestClass1 cl) { return ""; }
            
                @Root
                default Object root1(All<TestInterface> all) { return ""; }
            
                @DefaultComponent
                default TestClass1 defaultDependency() { return new TestClass1(); }
            
                default TestClass2 nonDefaultDependency() { return new TestClass2(); }
            }
            """);
        assertThat(draw.getNodes()).hasSize(4);
        draw.init();
    }

    @Test
    public void testEmptyAllOf() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                interface TestInterface {}
            
                @Root
                default Object allOfInterface(All<TestInterface> all) { return ""; }
            }
            """);
        assertThat(draw.getNodes()).hasSize(1);
        draw.init();
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
        draw.init();
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
        draw.init();
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
        draw.init();
    }

    @Test
    public void testRecursiveDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                interface TestInterface1 {
                    private void testPrivateFunction() {}
                }
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
        draw.init();
    }

    @Test
    public void testGraphDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root1(Graph graph) { return ""; }
            
                @Root
                default Object root2(RefreshableGraph graph) { return ""; }
            }
            """);
        Assertions.assertThat(draw.getNodes()).hasSize(2);
        draw.init();
    }

    @Test
    public void testNodeDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Root
                default Object root1(Node<String> node) { return ""; }
            
                default String component() { return ""; }
            }
            """);
        Assertions.assertThat(draw.getNodes()).hasSize(2);
        draw.init();
    }


    public @interface TestAnnotation {}

    @Test
    public void testTagIsInvalidOnFirstRound() {
        class TestProcessor extends AbstractProcessor {
            @Override
            public Set<String> getSupportedAnnotationTypes() {
                return Set.of(TestAnnotation.class.getCanonicalName());
            }

            @Override
            public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                for (var annotation : annotations) {
                    for (var element : roundEnv.getElementsAnnotatedWith(annotation)) {
                        var type = TypeSpec.interfaceBuilder("TestModule")
                            .addAnnotation(Module.class)
                            .addMethod(MethodSpec.methodBuilder("test")
                                .addModifiers(Modifier.DEFAULT, Modifier.PUBLIC)
                                .addAnnotation(AnnotationSpec.builder(Tag.class).addMember("value", "TestModule.class").build())
                                .returns(ClassName.get(testPackage(), "TestClass"))
                                .addCode("return new TestClass();\n")
                                .build())
                            .build();
                        try {
                            JavaFile.builder(testPackage(), type).build().writeTo(processingEnv.getFiler());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
                return false;
            }
        }

        var compileResult = compile(List.of(new KoraAppProcessor(), new TestProcessor()), """
            import io.koraframework.common.annotation.Tag;@KoraApp
            public interface ExampleApplication {
                @Root
                default Object root(@Tag(TestModule.class) TestClass object) { return java.util.Objects.requireNonNull(object); }
            }
            """, """
            @io.koraframework.kora.app.annotation.processor.DependencyTest.TestAnnotation
            public class TestClass {
            }
            """);
        if (compileResult.isFailed()) {
            throw compileResult.compilationException();
        }
    }

    @Test
    public void testValueOfBreaksCycleOfFinalClasses() throws Exception {
        for (var root : List.of("A", "B")) {
            var draw = compile("""
                @KoraApp
                public interface ExampleApplication {
                    final class A { public final ValueOf<B> b; public A(ValueOf<B> b) { this.b = b; } }
                    final class B { public final A a; public B(A a) { this.a = a; } }

                    @Root
                    default A a(ValueOf<B> b) { return new A(b); }
                    @Root
                    default B b(A a) { return new B(a); }
                }
                """.replace("@Root\n    default " + (root.equals("A") ? "B" : "A"), "default " + (root.equals("A") ? "B" : "A")));
            var graph = draw.init();
            var b = graph.get(draw.getNodes().stream().filter(n -> n.type().getTypeName().endsWith("$B")).findFirst().get());
            var a = b.getClass().getField("a").get(b);
            var valueOfB = (ValueOf<?>) a.getClass().getField("b").get(a);
            assertThat(valueOfB.get()).isSameAs(b);
            graph.release();
        }
    }

    @Test
    public void testPromiseOfInsideCycleBreaksCycleOfFinalClasses() throws Exception {
        for (var root : List.of("U", "S")) {
            var draw = compile("""
                @KoraApp
                public interface ExampleApplication {
                    final class T {}
                    final class U { public final PromiseOf<S> s; public U(PromiseOf<S> s) { this.s = s; } }
                    final class S { public final U u; public S(U u, T t) { this.u = u; } }

                    default T t() { return new T(); }
                    @Root
                    default U u(PromiseOf<S> s) { return new U(s); }
                    @Root
                    default S s(U u, T t) { return new S(u, t); }
                }
                """.replace("@Root\n    default " + (root.equals("U") ? "S" : "U"), "default " + (root.equals("U") ? "S" : "U")));
            var graph = draw.init();
            var s = graph.get(draw.getNodes().stream().filter(n -> n.type().getTypeName().endsWith("$S")).findFirst().get());
            var u = s.getClass().getField("u").get(s);
            var promiseOfS = (PromiseOf<?>) u.getClass().getField("s").get(u);
            assertThat(promiseOfS.get().orElseThrow()).isSameAs(s);
            graph.release();
        }
    }

    @Test
    public void testPromiseOfCycleTargetIsCreatedForUnconditionalRoot() throws Exception {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Tag(io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition.class)
                default GraphCondition failed() { return new io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition(); }

                final class T {}
                final class S { public S(U u, T t) {} }
                final class U { public final PromiseOf<S> s; public U(PromiseOf<S> s) { this.s = s; } }
                final class R { public R(S s) {} }

                default T t() { return new T(); }
                default S s(U u, T t) { return new S(u, t); }
                @Root
                default U u(PromiseOf<S> s) { return new U(s); }
                @Root
                @Conditional(tag = io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition.class)
                default R r(S s) { return new R(s); }
            }
            """);
        var graph = draw.init();
        var u = graph.get(draw.getNodes().stream().filter(n -> n.type().getTypeName().endsWith("$U")).findFirst().get());
        var promiseOfS = (PromiseOf<?>) u.getClass().getField("s").get(u);
        assertThat(promiseOfS.get()).isPresent();
    }

    @Test
    public void testValueOfOfSeveralConditionalComponentsDoesNotBreakCycle() {
        // ValueOf<X> resolves to a one-of over x1 and x2, deferring it to the one candidate on the cycle would lose the other
        var error = Assertions.catchThrowable(() -> compile("""
            @KoraApp
            public interface ExampleApplication {
                @Tag(io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition.class)
                default GraphCondition failed() { return new io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition(); }
                @Tag(io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.MatchesCondition.class)
                default GraphCondition matches() { return new io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.MatchesCondition(); }

                final class A { public A(ValueOf<X> x) {} }
                final class X { public X(A a) {} }

                @Root
                default A a(ValueOf<X> x) { return new A(x); }
                @Conditional(tag = io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.FailedCondition.class)
                default X x1(A a) { return new X(a); }
                @Conditional(tag = io.koraframework.kora.app.annotation.processor.ConditionalComponentTest.MatchesCondition.class)
                default X x2(A a) { return new X(a); }
            }
            """));
        assertThat(error).isNotNull();
        assertThat(compileResult.errors()).anyMatch(e -> e.getMessage(java.util.Locale.US).contains("Circular dependency found"));
    }

    @Test
    public void testWideAllCompilesInLinearTime() {
        var sb = new StringBuilder("@KoraApp\npublic interface ExampleApplication {\n");
        for (int i = 0; i < 150; i++) {
            sb.append("    default Integer c").append(i).append("() { return ").append(i).append("; }\n");
        }
        sb.append("    @Root\n    default String root(All<Integer> all) { int count = 0; for (var i : all) count++; return String.valueOf(count); }\n}\n");
        var started = System.nanoTime();
        var draw = compile(sb.toString());
        var took = java.time.Duration.ofNanos(System.nanoTime() - started);
        var graph = draw.init();
        var rootNode = draw.getNodes().stream().filter(n -> n.type().equals(String.class)).findFirst().get();
        assertThat(graph.get(rootNode)).isEqualTo("150");
        assertThat(took).isLessThan(java.time.Duration.ofSeconds(30));
    }
}
