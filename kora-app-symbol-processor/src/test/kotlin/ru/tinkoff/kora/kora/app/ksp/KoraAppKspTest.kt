@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.assertj.core.api.Assertions
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw
import ru.tinkoff.kora.application.graph.Node
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.kora.app.ksp.app.*
import ru.tinkoff.kora.kora.app.ksp.app.AppWithOptionalComponents.*
import ru.tinkoff.kora.ksp.common.CompilationErrorException
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Constructor
import java.util.function.Supplier
import java.util.stream.Collectors
import kotlin.reflect.KClass

class KoraAppKspTest {

    @Test
    fun testCompile() {
        val graphDraw = testClass(AppWithComponentsKotlin::class)
        Assertions.assertThat(graphDraw.nodes).hasSize(10)
        val materializedGraph = graphDraw.init().block()

        Assertions.assertThat(materializedGraph).isNotNull
    }

    @Test
    fun testGenericCase() {
        val graphDraw = testClass(AppWithComponents::class)
        val graph = graphDraw.init().block()
        Assertions.assertThat(graphDraw.nodes).hasSize(5)
    }

    @Test
    fun testNullableComponents() {
        val graphDraw = testClass(AppWithNullableComponents::class)
        val graph = graphDraw.init().block()
        Assertions.assertThat(graphDraw.nodes).hasSize(3)

        Assertions.assertThat(
            graph.get(findNodeOf(graphDraw, AppWithNullableComponents.NullableWithPresentValue::class.java)).value
        ).isNotNull
        Assertions.assertThat(
            graph.get(findNodeOf(graphDraw, AppWithNullableComponents.NullableWithMissingValue::class.java)).value
        ).isNull()
    }

    @Test
    fun testGenericArrays() {
        testClass(AppWithGenericWithArrays::class)
    }

    @Test
    fun testAutocreateComponent() {
        testClass(AppWithAutocreateComponent::class)
    }

    @Test
    fun testAppWithTags() {
        testClass(AppWithTag::class)
    }

    @Test
    fun appWithInheritanceComponents() {
        testClass(AppWithInheritanceComponents::class)
    }

    @Test
    fun testOptionalComponents() {
        val graphDraw = testClass(AppWithOptionalComponents::class)
        val graph = graphDraw.init().block()
        Assertions.assertThat(graphDraw.nodes).hasSize(9)
        Assertions.assertThat<PresentInGraph>(
            graph.get(
                findNodeOf(
                    graphDraw,
                    NotEmptyOptionalParameter::class.java
                )
            ).value
        ).isNotNull
        Assertions.assertThat<NotPresentInGraph>(
            graph.get(
                findNodeOf(
                    graphDraw,
                    EmptyOptionalParameter::class.java
                )
            ).value
        ).isNull()
        Assertions.assertThat<PresentInGraph>(
            graph.get(
                findNodeOf(
                    graphDraw,
                    NotEmptyValueOfOptional::class.java
                )
            ).value
        ).isNotNull
        Assertions.assertThat<NotPresentInGraph>(
            graph.get(
                findNodeOf(
                    graphDraw,
                    EmptyValueOfOptional::class.java
                )
            ).value
        ).isNull()
        Assertions.assertThat<PresentInGraph>(
            graph.get(
                findNodeOf(
                    graphDraw,
                    NotEmptyNullable::class.java
                )
            ).value
        ).isNotNull
        Assertions.assertThat<NotPresentInGraph>(
            graph.get(
                findNodeOf(
                    graphDraw,
                    EmptyNullable::class.java
                )
            ).value
        ).isNull()
    }

    @Test
    fun appWithProxies() {
        val graphDraw = testClass(AppWithValueOfComponents::class)!!
        val node1 = graphDraw.nodes[0]
        val node2 = graphDraw.nodes[1]
        val node3 = graphDraw.nodes[2]
        val graph = graphDraw.init().block()
        val value1 = graph[node1]
        val value2 = graph[node2]
        val value3 = graph[node3]
    }

    @Test
    fun appWithAllOfValueOf() {
        val graphDraw = testClass(AppWithAllOfValueOf::class)
        val node1 = graphDraw.nodes[0]
        val node2 = graphDraw.nodes[1]
        Assertions.assertThat(node1.dependentNodes).hasSize(1)
        val graph = graphDraw.init().block()
        val node1Value1 = graph[node1]
        val node2Value1 = graph[node2]
        graph.refresh(node1).block()
        val node1Value2 = graph[node1]
        val node2Value2 = graph[node2]
        Assertions.assertThat(node1Value1).isNotSameAs(node1Value2)
        Assertions.assertThat(node2Value1).isSameAs(node2Value2)
    }

    @Test
    fun appWithAllOf() {
        val graphDraw = testClass(AppWithAllOfComponents::class)
        Assertions.assertThat(graphDraw.nodes).hasSize(12)
        val graph = graphDraw.init().block()
        val classWithNonTaggedAllOf = findNodesOf(
            graphDraw,
            AppWithAllOfComponents.ClassWithAllOf::class.java,
            AppWithAllOfComponents.Superclass::class.java
        )
        Assertions.assertThat(classWithNonTaggedAllOf).hasSize(1)
        val l1 = graph[classWithNonTaggedAllOf[0]]
        Assertions.assertThat(l1.allOfSuperclass).hasSize(1)
        val classWithTaggedAllOf = findNodesOf(
            graphDraw,
            AppWithAllOfComponents.ClassWithAllOf::class.java,
            AppWithAllOfComponents.Superclass::class.java,
            AppWithAllOfComponents.Superclass::class.java
        )
        Assertions.assertThat(classWithTaggedAllOf).hasSize(1)
        val l2 = graph[classWithTaggedAllOf[0]]
        Assertions.assertThat(l2.allOfSuperclass).hasSize(1)
        val classWithAllOfNodesProxies =
            findNodesOf(graphDraw, AppWithAllOfComponents.ClassWithAllValueOf::class.java)
        Assertions.assertThat(classWithAllOfNodesProxies).hasSize(1)
        val lp = graph[classWithAllOfNodesProxies[0]]
        Assertions.assertThat(lp.allOfSuperclass).hasSize(5)
        val classWithInterfaces =
            findNodesOf(graphDraw, AppWithAllOfComponents.ClassWithInterfaces::class.java)
        Assertions.assertThat(classWithInterfaces).hasSize(1)
        val li = graph[classWithInterfaces[0]]
        Assertions.assertThat(li.allSomeInterfaces).hasSize(2)
        val classWithInterfacesValueOf =
            findNodesOf(graphDraw, AppWithAllOfComponents.ClassWithInterfacesValueOf::class.java)
        Assertions.assertThat(classWithInterfacesValueOf).hasSize(1)
        val lpi = graph[classWithInterfacesValueOf[0]]
        Assertions.assertThat(lpi.allSomeInterfaces).hasSize(2)
        val classWithAllOfAnyTag =
            findNodesOf(graphDraw, AppWithAllOfComponents.ClassWithAllOfAnyTag::class.java)
        Assertions.assertThat(classWithAllOfAnyTag).hasSize(1)
        val aoat = graph[classWithAllOfAnyTag[0]]
        Assertions.assertThat(aoat.class5All).hasSize(2)
    }

    @Test
    fun unresolvedDependency() {
        Assertions.assertThatThrownBy { testClass(AppWithUnresolvedDependency::class) }
            .isInstanceOfSatisfying(CompilationErrorException::class.java) { e ->
                SoftAssertions.assertSoftly { s: SoftAssertions ->
                    s.assertThat(e.messages)
                        .anyMatch { it.contains("Required dependency type was not found and can't be auto created: ru.tinkoff.kora.kora.app.ksp.app.AppWithUnresolvedDependency.Class3") }
                }
            }
    }

    @Test
    fun testCircularDependency() {
        Assertions.assertThatThrownBy { testClass(AppWithCircularDependency::class) }
            .isInstanceOfSatisfying(CompilationErrorException::class.java) { e ->
                SoftAssertions.assertSoftly { s: SoftAssertions ->
                    s.assertThat(e.messages).anyMatch { it.contains("There's a cycle in graph: ") }
                }
            }
    }

    @Test
    fun appWithComponentDescriptorCollision() {
        val graphDraw = testClass(AppWithComponentCollision::class)
        Assertions.assertThat(graphDraw.nodes).hasSize(3)
        val materializedGraph = graphDraw.init().block()
        Assertions.assertThat(materializedGraph).isNotNull
    }

    @Test
    fun appWithFactory() {
        testClass(AppWithFactories1::class).init().block()

        testClass(AppWithFactories2::class).init().block()

        testClass(AppWithFactories3::class).init().block()

        testClass(AppWithFactories4::class).init().block()

//        testClass(AppWithFactories5::class).init().block() delete or fix?

        Assertions.assertThatThrownBy { testClass(AppWithFactories6::class) }
            .isInstanceOfSatisfying(CompilationErrorException::class.java) { e ->
                SoftAssertions.assertSoftly { s: SoftAssertions ->
                    s.assertThat(e.messages).anyMatch { it.contains("There's a cycle in graph: ") }
                }
            }

        testClass(AppWithFactories7::class).init().block()

        testClass(AppWithFactories8::class).init().block()

        testClass(AppWithFactories9::class).init().block()
        Assertions.assertThatThrownBy { testClass(AppWithFactories10::class) }
            .isInstanceOfSatisfying(CompilationErrorException::class.java) { e ->
                SoftAssertions.assertSoftly { s: SoftAssertions ->
                    s.assertThat(e.messages).anyMatch {
                        it.contains("Required dependency type was not found and can't be auto created: java.io.Closeable")
                    }
                }
            }

//        Assertions.assertThatThrownBy { testClass(AppWithFactories11::class) }
//            .isInstanceOfSatisfying(CompilationErrorException::class.java) { e ->
//                SoftAssertions.assertSoftly { s: SoftAssertions ->
//                    s.assertThat(e.messages).contains(
//                        "Required dependency was not found and candidate class ru.tinkoff.kora.kora.app.ksp.app.AppWithFactories11.GenericClass<kotlin.String> is not final"
//                    )
//                }
//            } delete or fix?

        testClass(AppWithFactories12::class).init().block()

    }

    @Test
    fun appWithExtension() {
        val graphDraw = testClass(AppWithExtension::class)
        Assertions.assertThat(graphDraw.nodes).hasSize(4)
        val materializedGraph = graphDraw.init().block()
        Assertions.assertThat(materializedGraph).isNotNull
    }

    @Test
    fun extensionShouldHandleAnnotationsItProvidesAnnotationProcessorFor() {
        val graphDraw = testClass(AppWithProcessorExtension::class, listOf(AppWithProcessorExtension.TestProcessorProvider()))
        Assertions.assertThat(graphDraw.nodes).hasSize(2)
        val materializedGraph = graphDraw.init().block()
        Assertions.assertThat(materializedGraph).isNotNull
    }

    @Test
    fun appWithComonentDescriptorCollisionAndDirect() {
        Assertions.assertThatThrownBy {
            testClass(
                AppWithComponentCollisionAndDirect::class
            )
        }
            .isInstanceOfSatisfying(CompilationErrorException::class.java) { e ->
                SoftAssertions.assertSoftly { s: SoftAssertions ->
                    s.assertThat(e.messages)
                        .anyMatch { it.contains("More than one component matches dependency claim ru.tinkoff.kora.kora.app.ksp.app.AppWithComponentCollisionAndDirect.Class1 tag=[]:") }
                }
            }
    }

    @Test
    fun appWithMultipleTags() {
        val graphDraw = testClass(AppWithMultipleTags::class)
        Assertions.assertThat(graphDraw.nodes).hasSize(12)
        val graph = graphDraw.init().block()
        Assertions.assertThat(graph).isNotNull
        val nonTaggedClass3 = findNodesOf(graphDraw, AppWithMultipleTags.Class3::class.java)
        Assertions.assertThat(nonTaggedClass3).hasSize(1)
        val anyTaggedClass3 = findNodesOf(graphDraw, AppWithMultipleTags.Class3::class.java, AppWithMultipleTags::class.java) as List<Node<AppWithMultipleTags.Class3>>
        Assertions.assertThat(anyTaggedClass3).hasSize(1)
        Assertions.assertThat(graph[anyTaggedClass3[0]].class1s).hasSize(4)
        val tag1TaggedClass3 =
            findNodesOf(graphDraw, AppWithMultipleTags.Class3::class.java, AppWithMultipleTags.Tag1::class.java)
        Assertions.assertThat(tag1TaggedClass3).hasSize(1)
        Assertions.assertThat(graph[tag1TaggedClass3[0]].class1s).hasSize(1)
        val tag2Tag3Taggedlass3 = findNodesOf(
            graphDraw,
            AppWithMultipleTags.Class3::class.java,
            AppWithMultipleTags.Tag2::class.java,
            AppWithMultipleTags.Tag3::class.java
        )
        Assertions.assertThat(tag2Tag3Taggedlass3).hasSize(1)
        Assertions.assertThat(graph[tag2Tag3Taggedlass3[0]].class1s).hasSize(2)
        val tag4TaggedClass3 = findNodesOf(graphDraw, AppWithMultipleTags.Class3::class.java, AppWithMultipleTags.Tag4::class.java)
        Assertions.assertThat(tag4TaggedClass3).hasSize(1)
        Assertions.assertThat(graph[tag4TaggedClass3[0]].class1s).hasSize(1)
    }

    @Test
    fun appWithWrappedComponent() {
        val graphDraw = testClass(AppWithWrappedDependency::class)
        Assertions.assertThat(graphDraw.nodes).hasSize(7)
        val materializedGraph = graphDraw.init().block()
        Assertions.assertThat(materializedGraph).isNotNull
    }

    @Test
    fun appWithNestedClasses() {
        val graphDraw = testClass(AppWithNestedClasses::class)
        Assertions.assertThat(graphDraw.nodes).hasSize(2)
        val materializedGraph = graphDraw.init().block()
        Assertions.assertThat(materializedGraph).isNotNull
    }

    @Test
    fun appWithLazyComponents() {
        val graphDraw = testClass(AppWithLazyComponents::class)
        Assertions.assertThat(graphDraw.nodes).hasSize(3)
        val materializedGraph = graphDraw.init().block()
        Assertions.assertThat(materializedGraph).isNotNull
    }

    @Test
    fun appWithModuleOf() {
        val graphDraw = testClass(AppWithModuleOf::class)
        Assertions.assertThat(graphDraw.nodes).hasSize(2)
        val materializedGraph = graphDraw.init().block()
        Assertions.assertThat(materializedGraph).isNotNull
    }

    @Test
    fun appWithClassWithComponentOf() {
        val graphDraw = testClass(AppWithClassWithComponentOf::class)
        Assertions.assertThat(graphDraw.nodes).hasSize(5)
        val materializedGraph = graphDraw.init().block()
        Assertions.assertThat(materializedGraph).isNotNull
    }

    @Test
    fun appWithInterceptor() {
        val graphDraw = testClass(AppWithInterceptor::class)
        Assertions.assertThat(graphDraw.nodes).hasSize(4)
        val materializedGraph = graphDraw.init().block()
        Assertions.assertThat(materializedGraph).isNotNull
        materializedGraph.release().block()
    }

    @Test
    fun appWithPromiseOf() {
        val graphDraw = testClass(AppWithPromiseOf::class)
        Assertions.assertThat(graphDraw.nodes).hasSize(5)
        val materializedGraph = graphDraw.init().block()
        Assertions.assertThat(materializedGraph).isNotNull
        materializedGraph.release().block()
    }

    @Test
    fun appWithOverridenModule() {
        val graphDraw = testClass(AppWithOverridenModule::class)
        Assertions.assertThat(graphDraw.nodes).hasSize(2)
        val materializedGraph = graphDraw.init().block()
        Assertions.assertThat(materializedGraph).isNotNull
        materializedGraph.release().block()
    }

    @Test
    fun appWithExactDependencyMatch() {
        val graphDraw = testClass(AppWithExactMatch::class)
        Assertions.assertThat(graphDraw.nodes).hasSize(8)
    }

    @Test
    fun appWithCycleProxy() {
        val graphDraw = testClass(AppWithCycleProxy::class)
        Assertions.assertThat(graphDraw.nodes).hasSize(7)
        val graph = graphDraw.init().block();
        Assertions.assertThat(graph).isNotNull;
    }

    @Test
    fun appPart() {
        val classLoader: ClassLoader = symbolProcess(
            AppWithAppPart::class, KoraAppProcessorProvider()
        )
        val clazz = classLoader.loadClass(AppWithAppPart::class.java.name + "SubmoduleImpl")
        Assertions.assertThat(clazz).isNotNull
            .isInterface
            .hasMethods("_component0", "_component1")
            .matches { cls -> AppWithAppPart::class.java.isAssignableFrom(cls) }
            .matches { cls -> !AppWithAppPart.Module::class.java.isAssignableFrom(cls) }
        val targetFile1 = "src/test/kotlin/" + AppWithAppPartApp::class.java.name.replace('.', '/') + ".kt"
        val modulePath = AppWithAppPart::class.java.protectionDomain.codeSource.location.path.substringBefore("/build")
//        classLoader = symbolProcessFiles(listOf(targetFile1), listOf(KoraAppProcessorProvider())) todo kotlinc cleans directories
//        val appClazz = classLoader.loadClass(AppWithAppPartApp::class.java.name + "Graph")
    }

    private fun <T> findNodeOf(graphDraw: ApplicationGraphDraw, type: Class<T>, vararg tags: Class<*>): Node<T> {
        val nodes = findNodesOf(graphDraw, type, *tags)
        check(nodes.size == 1)
        return nodes[0]
    }

    private fun <T> findNodesOf(graphDraw: ApplicationGraphDraw, type: Class<T>, vararg tags: Class<*>): List<Node<T>> {
        val graph = graphDraw.init().block()
        val anyTag = listOf(*tags).contains(Tag.Any::class.java)
        val nonTagged = tags.isEmpty()
        return graphDraw.nodes
            .filter { type.isInstance(graph[it]) }
            .filter { node ->
                if (anyTag) {
                    return@filter true
                }
                if (nonTagged) {
                    return@filter node.tags().isEmpty()
                }
                return@filter tags.all { listOf(*node.tags()).contains(it) }
            }.map { it as Node<T> }
            .toList()
    }

    fun testClass(targetClass: KClass<*>, processorProviders: List<SymbolProcessorProvider> = listOf()): ApplicationGraphDraw {
        return try {
            val graphClass = targetClass.qualifiedName + "Graph"
            val processorsArray = (processorProviders + KoraAppProcessorProvider()).toTypedArray()
            val classLoader = symbolProcess(targetClass, *processorsArray)
            val clazz = try {
                classLoader.loadClass(graphClass)
            } catch (e: ClassNotFoundException) {
                val packageClasses = classLoader.getResourceAsStream(targetClass.java.packageName.replace('.', '/')).use { stream ->
                    val reader = BufferedReader(InputStreamReader(stream))
                    reader.lines().filter { it.endsWith(".class") }.map { it.replace(".class", "") }.collect(Collectors.joining("; "))
                }
                fail("Can't load class $graphClass, classes in package: $packageClasses", e)
            }
            val constructors = clazz.constructors as Array<Constructor<out Supplier<out ApplicationGraphDraw>>>
            constructors[0].newInstance().get()
        } catch (e: Exception) {
            if (e.cause != null) {
                throw (e.cause as Exception)
            }
            throw e
        }
    }
}


