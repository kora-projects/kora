package ru.tinkoff.kora.kora.app.annotation.processor;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.annotation.processor.common.TestUtils;
import ru.tinkoff.kora.annotation.processor.common.TestUtils.CompilationErrorException;
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw;
import ru.tinkoff.kora.application.graph.Node;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.kora.app.annotation.processor.app.*;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.type;

class KoraAppProcessorTest {
    static {
        if (LoggerFactory.getLogger("ROOT") instanceof Logger log) {
            log.setLevel(Level.OFF);
        }
    }

    @Test
    void testGenericCase() throws Throwable {
        var graphDraw = testClass(AppWithComponents.class);
        var graph = graphDraw.init().block();
        Assertions.assertThat(graphDraw.getNodes()).hasSize(5);
    }

    @Test
    void testOptionalComponents() throws Throwable {
        var graphDraw = testClass(AppWithOptionalComponents.class);

        var graph = graphDraw.init().block();
        Assertions.assertThat(graphDraw.getNodes()).hasSize(9);

        Assertions.assertThat(graph.get(findNodeOf(graphDraw, AppWithOptionalComponents.NotEmptyOptionalParameter.class)).value()).isNotNull();
        Assertions.assertThat(graph.get(findNodeOf(graphDraw, AppWithOptionalComponents.EmptyOptionalParameter.class)).value()).isNull();
        Assertions.assertThat(graph.get(findNodeOf(graphDraw, AppWithOptionalComponents.NotEmptyValueOfOptional.class)).value()).isNotNull();
        Assertions.assertThat(graph.get(findNodeOf(graphDraw, AppWithOptionalComponents.EmptyValueOfOptional.class)).value()).isNull();
        Assertions.assertThat(graph.get(findNodeOf(graphDraw, AppWithOptionalComponents.NotEmptyNullable.class)).value()).isNotNull();
        Assertions.assertThat(graph.get(findNodeOf(graphDraw, AppWithOptionalComponents.EmptyNullable.class)).value()).isNull();
    }

    @Test
    void testGenericArrays() throws Throwable {
        testClass(AppWithGenericWithArrays.class);
    }

    @Test
    void testAutocreateComponent() throws Throwable {
        testClass(AppWithAutocreateComponent.class);
    }

    @Test
    void testAppWithTags() throws Throwable {
        testClass(AppWithTag.class);
    }

    @Test
    void appWithInheritanceComponents() throws Throwable {
        testClass(AppWithInheritanceComponents.class);
    }

    @Test
    void appWithProxies() throws Throwable {
        var graphDraw = testClass(AppWithValueOfComponents.class);
        var node1 = graphDraw.getNodes().get(0);
        var node2 = graphDraw.getNodes().get(1);
        var node3 = graphDraw.getNodes().get(2);
        var graph = graphDraw.init().block();
        var value1 = graph.get(node1);
        var value2 = graph.get(node2);
        var value3 = graph.get(node3);
    }

    @Test
    void appWithAllOfValueOf() throws Throwable {
        var graphDraw = testClass(AppWithAllOfValueOf.class);
        var node1 = graphDraw.getNodes().get(0);
        var node2 = graphDraw.getNodes().get(1);
        assertThat(node1.getDependentNodes()).hasSize(1);

        var graph = graphDraw.init().block();
        var node1Value1 = graph.get(node1);
        var node2Value1 = graph.get(node2);

        graph.refresh(node1).block();

        var node1Value2 = graph.get(node1);
        var node2Value2 = graph.get(node2);
        Assertions.assertThat(node1Value1).isNotSameAs(node1Value2);
        Assertions.assertThat(node2Value1).isSameAs(node2Value2);
    }

    @Test
    void appWithAllOf() throws Throwable {
        var graphDraw = testClass(AppWithAllOfComponents.class);
        Assertions.assertThat(graphDraw.getNodes()).hasSize(12);
        var graph = graphDraw.init().block();

        var classWithNonTaggedAllOf = this.findNodesOf(graphDraw, AppWithAllOfComponents.ClassWithAllOf.class, AppWithAllOfComponents.Superclass.class);
        Assertions.assertThat(classWithNonTaggedAllOf).hasSize(1);
        var l1 = graph.get(classWithNonTaggedAllOf.get(0));
        Assertions.assertThat(l1.allOfSuperclass()).hasSize(1);

        var classWithTaggedAllOf = this.findNodesOf(graphDraw, AppWithAllOfComponents.ClassWithAllOf.class, AppWithAllOfComponents.Superclass.class, AppWithAllOfComponents.Superclass.class);
        Assertions.assertThat(classWithTaggedAllOf).hasSize(1);
        var l2 = graph.get(classWithTaggedAllOf.get(0));
        Assertions.assertThat(l2.allOfSuperclass()).hasSize(1);


        var classWithAllOfNodesProxies = this.findNodesOf(graphDraw, AppWithAllOfComponents.ClassWithAllValueOf.class);
        Assertions.assertThat(classWithAllOfNodesProxies).hasSize(1);
        var lp = graph.get(classWithAllOfNodesProxies.get(0));
        Assertions.assertThat(lp.allOfSuperclass()).hasSize(5);


        var classWithInterfaces = this.findNodesOf(graphDraw, AppWithAllOfComponents.ClassWithInterfaces.class);
        Assertions.assertThat(classWithInterfaces).hasSize(1);
        var li = graph.get(classWithInterfaces.get(0));
        Assertions.assertThat(li.allSomeInterfaces()).hasSize(2);

        var classWithInterfacesValueOf = this.findNodesOf(graphDraw, AppWithAllOfComponents.ClassWithInterfacesValueOf.class);
        Assertions.assertThat(classWithInterfacesValueOf).hasSize(1);
        var lpi = graph.get(classWithInterfacesValueOf.get(0));
        Assertions.assertThat(lpi.allSomeInterfaces()).hasSize(2);


        var classWithAllOfAnyTag = this.findNodesOf(graphDraw, AppWithAllOfComponents.ClassWithAllOfAnyTag.class);
        Assertions.assertThat(classWithAllOfAnyTag).hasSize(1);
        var aoat = graph.get(classWithAllOfAnyTag.get(0));
        Assertions.assertThat(aoat.class5All()).hasSize(2);
    }

    @Test
    void unresolvedDependency() {
        assertThatThrownBy(() -> testClass(AppWithUnresolvedDependency.class))
            .isInstanceOfSatisfying(CompilationErrorException.class, e -> SoftAssertions.assertSoftly(s -> {
                s.assertThat(e.getMessage()).isEqualTo("""
                    Required dependency was not found: ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithUnresolvedDependency.Class3
                      Requested at: ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithUnresolvedDependency.class2(ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithUnresolvedDependency.Class3)""");
                s.assertThat(e.diagnostics.get(0).getPosition()).isEqualTo(326);
                s.assertThat(e.diagnostics.get(0).getLineNumber()).isEqualTo(12);
                s.assertThat(e.diagnostics.get(0).getSource().getName()).isEqualTo("src/test/java/ru/tinkoff/kora/kora/app/annotation/processor/app/AppWithUnresolvedDependency.java");
            }));
    }

    @Test
    void testCircularDependency() {
        assertThatThrownBy(() -> testClass(AppWithCircularDependency.class))
            .isInstanceOfSatisfying(CompilationErrorException.class, e -> SoftAssertions.assertSoftly(s -> {
                s.assertThat(e.getMessage()).startsWith("There's a cycle in graph: ");
                s.assertThat(e.diagnostics.get(0).getSource().getName()).isEqualTo("src/test/java/ru/tinkoff/kora/kora/app/annotation/processor/app/AppWithCircularDependency.java");
            }));
    }

    @Test
    void appWithComonentDescriptorCollision() throws Throwable {
        var graphDraw = testClass(AppWithComponentCollision.class);
        Assertions.assertThat(graphDraw.getNodes()).hasSize(3);
        var materializedGraph = graphDraw.init().block();
        Assertions.assertThat(materializedGraph).isNotNull();
    }


    @Test
    void appWithFactory() throws Throwable {
        testClass(AppWithFactories1.class).init().block();
        testClass(AppWithFactories2.class).init().block();
        testClass(AppWithFactories3.class).init().block();
        testClass(AppWithFactories4.class).init().block();
//        testClass(AppWithFactories5.class).init().block(); TODO больше не нужно
        assertThatThrownBy(() -> testClass(AppWithFactories6.class))
            .isInstanceOf(CompilationErrorException.class)
            .hasMessageStartingWith("There's a cycle in graph:");
        testClass(AppWithFactories7.class).init().block();
        testClass(AppWithFactories8.class).init().block();
        testClass(AppWithFactories9.class).init().block();
        assertThatThrownBy(() -> testClass(AppWithFactories10.class))
            .isInstanceOf(CompilationErrorException.class)
            .hasMessageStartingWith("Required dependency was not found: java.io.Closeable")
            .asInstanceOf(type(CompilationErrorException.class))
            .extracting(CompilationErrorException::getDiagnostics, list(Diagnostic.class))
            .anySatisfy(d -> {
                assertThat(d.getKind()).isEqualTo(Diagnostic.Kind.ERROR);
                assertThat(d.getMessage(Locale.ENGLISH)).isEqualTo("""
                    Required dependency was not found: java.io.Closeable
                      Requested at: ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithFactories10.mock1(java.io.Closeable)
                    """.trim());
            })
        ;
//        assertThatThrownBy(() -> testClass(AppWithFactories11.class))
//            .isInstanceOf(CompilationErrorException.class)
//            .hasMessageContaining("Required dependency was not found and candidate class ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithFactories11.GenericClass<java.lang.String> is not final")
//            .asInstanceOf(type(CompilationErrorException.class))
//            .extracting(CompilationErrorException::getDiagnostics, list(Diagnostic.class))
//            .anySatisfy(d -> {
//                assertThat(d.getKind()).isEqualTo(Diagnostic.Kind.ERROR);
//                assertThat(d.getMessage(Locale.ENGLISH)).isEqualTo("""
//                      Required dependency was not found and candidate class java.lang.Long has more then one public constructor
//                        Requested at: ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithFactories11.<T>factory2(java.lang.Long)
//                     \s
//                      Factory ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithFactories11#factory2 failed to produce component because of missing dependency of type java.lang.Long
//                      Factory ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithFactories11#factory1 failed to produce component because of missing dependency of type java.io.Closeable
//                        Required dependency implementation was not found java.io.Closeable, check if it is declared or appropriate module is declared in @KoraApp
//                        Requested at: ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithFactories11.<T>factory1(java.io.Closeable)
//                     \s
//                      Required dependency was not found and candidate class ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithFactories11.GenericClass<java.lang.String> is not final
//                      Requested at: ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithFactories11.mock1(ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithFactories11.GenericClass<java.lang.String>)
//                    """.stripTrailing());
//
//            });
        testClass(AppWithFactories12.class).init().block();
    }

    @Test
    void appWithExtension() throws Throwable {
        var graphDraw = testClass(AppWithExtension.class);
        Assertions.assertThat(graphDraw.getNodes()).hasSize(3);
        var materializedGraph = graphDraw.init().block();
        Assertions.assertThat(materializedGraph).isNotNull();
    }

    @Test
    void extensionShouldHandleAnnotationsItProvidesAnnotationProcessorFor() throws Throwable {
        var graphDraw = testClass(AppWithProcessorExtension.class, List.of(new AppWithProcessorExtension.TestProcessor()));
        Assertions.assertThat(graphDraw.getNodes()).hasSize(2);
        var materializedGraph = graphDraw.init().block();
        Assertions.assertThat(materializedGraph).isNotNull();
    }

    @Test
    void appWithComponentDescriptorCollisionAndDirect() {
        assertThatThrownBy(() -> testClass(AppWithComponentCollisionAndDirect.class))
            .isInstanceOfSatisfying(CompilationErrorException.class, e -> SoftAssertions.assertSoftly(s -> {
                var error = e.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).findFirst().get();
                s.assertThat(error.getMessage(Locale.US))
                    .startsWith("More than one component matches dependency claim ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithComponentCollisionAndDirect.Class1:");

                s.assertThat(error.getMessage(Locale.US)).contains("FromModuleComponent[type=ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithComponentCollisionAndDirect.Class1, module=MixedInModule[element=ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithComponentCollisionAndDirect], tags=[], method=c1(), methodParameterTypes=[], typeVariables=[]]");
                s.assertThat(error.getMessage(Locale.US)).contains("FromModuleComponent[type=ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithComponentCollisionAndDirect.Class1, module=MixedInModule[element=ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithComponentCollisionAndDirect], tags=[], method=c2(), methodParameterTypes=[], typeVariables=[]]");
                s.assertThat(error.getMessage(Locale.US)).contains("FromModuleComponent[type=ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithComponentCollisionAndDirect.Class1, module=MixedInModule[element=ru.tinkoff.kora.kora.app.annotation.processor.app.AppWithComponentCollisionAndDirect], tags=[], method=c3(), methodParameterTypes=[], typeVariables=[]]");
            }));
    }

    @Test
    void appWithMultipleTags() throws Throwable {
        var graphDraw = testClass(AppWithMultipleTags.class);
        Assertions.assertThat(graphDraw.getNodes()).hasSize(12);
        var graph = graphDraw.init().block();
        Assertions.assertThat(graph).isNotNull();

        var nonTaggedClass3 = findNodesOf(graphDraw, AppWithMultipleTags.Class3.class);
        Assertions.assertThat(nonTaggedClass3).hasSize(1);

        var anyTaggedClass3 = findNodesOf(graphDraw, AppWithMultipleTags.Class3.class, AppWithMultipleTags.class);
        Assertions.assertThat(anyTaggedClass3).hasSize(1);
        Assertions.assertThat(graph.get(anyTaggedClass3.get(0)).class1s()).hasSize(4);

        var tag1TaggedClass3 = findNodesOf(graphDraw, AppWithMultipleTags.Class3.class, AppWithMultipleTags.Tag1.class);
        Assertions.assertThat(tag1TaggedClass3).hasSize(1);
        Assertions.assertThat(graph.get(tag1TaggedClass3.get(0)).class1s()).hasSize(1);

        var tag2Tag3Taggedlass3 = findNodesOf(graphDraw, AppWithMultipleTags.Class3.class, AppWithMultipleTags.Tag2.class, AppWithMultipleTags.Tag3.class);
        Assertions.assertThat(tag2Tag3Taggedlass3).hasSize(1);
        Assertions.assertThat(graph.get(tag2Tag3Taggedlass3.get(0)).class1s()).hasSize(2);


        var tag4TaggedClass3 = findNodesOf(graphDraw, AppWithMultipleTags.Class3.class, AppWithMultipleTags.Tag4.class);
        Assertions.assertThat(tag4TaggedClass3).hasSize(1);
        Assertions.assertThat(graph.get(tag4TaggedClass3.get(0)).class1s()).hasSize(1);
    }

    @Test
    void appWithWrappedComponent() throws Exception {
        var graphDraw = testClass(AppWithWrappedDependency.class);
        Assertions.assertThat(graphDraw.getNodes()).hasSize(7);
        var materializedGraph = graphDraw.init().block();
        Assertions.assertThat(materializedGraph).isNotNull();
    }

    @Test
    void appWithNestedClasses() throws Exception {
        var graphDraw = testClass(AppWithNestedClasses.class);
        Assertions.assertThat(graphDraw.getNodes()).hasSize(2);
        var materializedGraph = graphDraw.init().block();
        Assertions.assertThat(materializedGraph).isNotNull();
    }

    @Test
    void appWithLazyComponents() throws Exception {
        var graphDraw = testClass(AppWithLazyComponents.class);
        Assertions.assertThat(graphDraw.getNodes()).hasSize(4);
        var materializedGraph = graphDraw.init().block();
        Assertions.assertThat(materializedGraph).isNotNull();
    }

    @Test
    void appWithModuleOf() throws Exception {
        var graphDraw = testClass(AppWithModuleOf.class);
        Assertions.assertThat(graphDraw.getNodes()).hasSize(2);
        var materializedGraph = graphDraw.init().block();
        Assertions.assertThat(materializedGraph).isNotNull();
    }

    @Test
    void appWithClassWithComponentOf() throws Exception {
        var graphDraw = testClass(AppWithClassWithComponentOf.class);
        Assertions.assertThat(graphDraw.getNodes()).hasSize(5);
        var materializedGraph = graphDraw.init().block();
        Assertions.assertThat(materializedGraph).isNotNull();
    }

    @Test
    void appWithPromiseOf() throws Exception {
        var graphDraw = testClass(AppWithPromiseOf.class);
        Assertions.assertThat(graphDraw.getNodes()).hasSize(6);
        var materializedGraph = graphDraw.init().block();
        Assertions.assertThat(materializedGraph).isNotNull();

        materializedGraph.release().block();
    }

    @Test
    void appWithOverridenModule() throws Exception {
        var graphDraw = testClass(AppWithOverridenModule.class);
        Assertions.assertThat(graphDraw.getNodes()).hasSize(2);
        var materializedGraph = graphDraw.init().block();
        Assertions.assertThat(materializedGraph).isNotNull();

        materializedGraph.release().block();
    }

    @Test
    void appWithExactDependencyMatch() throws Exception {
        var graphDraw = testClass(AppWithExactMatch.class);
        Assertions.assertThat(graphDraw.getNodes()).hasSize(8);
    }

    @Test
    void appWithComponentsWithSameName() throws Exception {
        var graphDraw = testClass(AppWithComponentsWithSameName.class);
        Assertions.assertThat(graphDraw.getNodes()).hasSize(4);
    }

    @Test
    void appPart() throws Exception {
        var classLoader = TestUtils.annotationProcess(AppWithAppPart.class, new KoraAppProcessor());
        var clazz = classLoader.loadClass(AppWithAppPart.class.getName() + "SubmoduleImpl");
        Assertions.assertThat(clazz).isNotNull()
            .isInterface()
            .hasDeclaredMethods("_component0", "_component1")
            .matches(AppWithAppPart.class::isAssignableFrom)
            .matches(Predicate.not(AppWithAppPart.Module.class::isAssignableFrom));
        var targetFile1 = "src/test/java/" + AppWithAppPartApp.class.getName().replace('.', '/') + ".java";
        var targetFile2 = "in-test-generated/classes/" + clazz.getCanonicalName().replace('.', '/') + ".class";

        classLoader = TestUtils.annotationProcessFiles(List.of(targetFile1, targetFile2), false, new KoraAppProcessor());
        var appClazz = classLoader.loadClass(AppWithAppPartApp.class.getName() + "Graph");
    }

    @Test
    void appWithDefaultComponent() throws Throwable {
        var graphDraw = testClass(AppWithDefaultComponent.class);
        Assertions.assertThat(graphDraw.getNodes()).hasSize(3);
        var graph = graphDraw.init().block();
        Assertions.assertThat(graph).isNotNull();

        var class1Nodes = findNodesOf(graphDraw, AppWithDefaultComponent.Class1.class);
        Assertions.assertThat(class1Nodes).hasSize(1);
        var class1Node = class1Nodes.get(0);
        assertThat(graph.get(class1Node).value()).isEqualTo(2);
    }

    @SuppressWarnings("unchecked")
    <T> Node<T> findNodeOf(ApplicationGraphDraw graphDraw, Class<T> type, Class<?>... tags) {
        var nodes = findNodesOf(graphDraw, type, tags);

        if (nodes.size() != 1) {
            throw new IllegalStateException();
        }
        return (Node<T>) nodes.get(0);
    }

    @SuppressWarnings("unchecked")
    <T> List<Node<? extends T>> findNodesOf(ApplicationGraphDraw graphDraw, Class<T> type, Class<?>... tags) {
        var graph = graphDraw.init().block();
        var anyTag = Arrays.asList(tags).contains(Tag.Any.class);
        var nonTagged = tags.length == 0;
        return graphDraw.getNodes().stream()
            .filter(node -> type.isInstance(graph.get(node)))
            .map(node -> (Node<? extends T>) node)
            .filter(node -> {
                if (anyTag) {
                    return true;
                }
                if (nonTagged) {
                    return node.tags().length == 0;
                }
                return Arrays.stream(tags).allMatch(tag -> Arrays.asList(node.tags()).contains(tag));
            })
            .collect(Collectors.toList());
    }

    ApplicationGraphDraw testClass(Class<?> targetClass) throws Exception {
        return testClass(targetClass, List.of());
    }

    ApplicationGraphDraw testClass(Class<?> targetClass, List<Processor> processors) throws Exception {
        try {
            var processorsArray = new ArrayList<>(processors).toArray(new Processor[processors.size() + 1]);
            processorsArray[processors.size()] = new KoraAppProcessor();

            var classLoader = TestUtils.annotationProcess(targetClass, processorsArray);
            var clazz = classLoader.loadClass(targetClass.getName() + "Graph");
            @SuppressWarnings("unchecked")
            var constructors = (Constructor<? extends Supplier<? extends ApplicationGraphDraw>>[]) clazz.getConstructors();
            return constructors[0].newInstance().get();
        } catch (Exception e) {
            if (e.getCause() != null) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }
}
