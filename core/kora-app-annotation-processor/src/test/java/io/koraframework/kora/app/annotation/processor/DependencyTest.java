package io.koraframework.kora.app.annotation.processor;

import com.palantir.javapoet.*;
import io.koraframework.common.annotation.Module;
import io.koraframework.common.annotation.Tag;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    public void testAllWithMultipleDefaultsOnly() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                interface TestInterface { String name(); }

                @Root
                default String root(All<TestInterface> all) {
                    var sb = new StringBuilder();
                    for (var item : all) sb.append(item.name());
                    return sb.toString();
                }

                @DefaultComponent
                default TestInterface first() { return () -> "a"; }

                @DefaultComponent
                default TestInterface second() { return () -> "b"; }
            }
            """);
        assertThat(draw.getNodes()).hasSize(3);
        var g = draw.init();
        assertThat(draw.getNodes().stream().<Object>map(g::get).toList()).contains("ab");
    }

    @Test
    public void testAllSkipsDefaultRequestedDirectlyBeforeAll() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                interface TestInterface { String name(); }
                class DefaultImpl implements TestInterface { public String name() { return "default"; } }

                @Root
                default Integer root1(DefaultImpl d) { return 1; }

                @Root
                default String root2(All<TestInterface> all) {
                    var sb = new StringBuilder();
                    for (var item : all) sb.append(item.name()).append(';');
                    return sb.toString();
                }

                @DefaultComponent
                default DefaultImpl defaultDependency() { return new DefaultImpl(); }

                default TestInterface nonDefaultDependency() { return () -> "custom"; }
            }
            """);
        var g = draw.init();
        assertThat(draw.getNodes().stream().<Object>map(g::get).toList()).contains("custom;");
    }

    @Test
    public void testAllSkipsDefaultRequestedDirectlyAfterAll() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                interface TestInterface { String name(); }
                class DefaultImpl implements TestInterface { public String name() { return "default"; } }

                @Root
                default String root1(All<TestInterface> all) {
                    var sb = new StringBuilder();
                    for (var item : all) sb.append(item.name()).append(';');
                    return sb.toString();
                }

                @Root
                default Integer root2(DefaultImpl d) { return 1; }

                @DefaultComponent
                default DefaultImpl defaultDependency() { return new DefaultImpl(); }

                default TestInterface nonDefaultDependency() { return () -> "custom"; }
            }
            """);
        var g = draw.init();
        assertThat(draw.getNodes().stream().<Object>map(g::get).toList()).contains("custom;");
    }

    @Test
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


    @Test
    public void testTaggedOptionalDependency() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                @Tag(Integer.class)
                default String tagged() { return "tagged"; }

                default String untagged() { return "untagged"; }

                @Root
                default Object present(@Tag(Integer.class) Optional<String> value) { return value; }

                @Root
                default Object presentValueOf(@Tag(Integer.class) Optional<ValueOf<String>> value) { return value; }

                @Root
                default Object absent(@Tag(Integer.class) Optional<Long> value) { return value; }
            }
            """);
        var g = draw.init();
        assertThat(draw.getNodes().stream().<Object>map(g::get).toList())
            .contains(Optional.of("tagged"), Optional.empty())
            .doesNotContain(Optional.of("untagged"));
    }

    @Test
    public void testNodeOfWrappedComponent() {
        var draw = compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass {}

                default Wrapped<TestClass> component() { return TestClass::new; }

                @Root
                default Object root(Node<Wrapped<TestClass>> node) { return ""; }
            }
            """);
        assertThat(draw.getNodes()).hasSize(2);
        draw.init();

        assertThatThrownBy(() -> compile("""
            @KoraApp
            public interface ExampleApplication {
                class TestClass {}

                default Wrapped<TestClass> component() { return TestClass::new; }

                @Root
                default Object root(Node<TestClass> node) { return ""; }
            }
            """))
            .hasMessageContaining("component provided as Wrapped<T>");
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


}
