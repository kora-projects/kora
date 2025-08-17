@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package ru.tinkoff.kora.kora.app.ksp

import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw
import ru.tinkoff.kora.application.graph.internal.NodeImpl
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.kora.app.ksp.app.*
import ru.tinkoff.kora.kora.app.ksp.app.AppWithOptionalComponents.*
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest.ProcessorOptions
import ru.tinkoff.kora.ksp.common.CompilationErrorException
import ru.tinkoff.kora.ksp.common.symbolProcess
import ru.tinkoff.kora.ksp.common.symbolProcessFiles
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
        assertThat(graphDraw.nodes).hasSize(10)
        val materializedGraph = graphDraw.init()

        assertThat(materializedGraph).isNotNull
    }

    @Test
    fun testGenericCase() {
        val graphDraw = testClass(AppWithComponents::class)
        val graph = graphDraw.init()
        assertThat(graphDraw.nodes).hasSize(5)
    }

    @Test
    fun testNullableComponents() {
        val graphDraw = testClass(AppWithNullableComponents::class)
        val graph = graphDraw.init()
        assertThat(graphDraw.nodes).hasSize(3)

        assertThat(
            graph.get(findNodeOf(graphDraw, AppWithNullableComponents.NullableWithPresentValue::class.java)).value
        ).isNotNull
        assertThat(
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
        val graph = graphDraw.init()
        assertThat(graphDraw.nodes).hasSize(9)
        assertThat<PresentInGraph>(
            graph.get(
                findNodeOf(
                    graphDraw,
                    NotEmptyOptionalParameter::class.java
                )
            ).value
        ).isNotNull
        assertThat<NotPresentInGraph>(
            graph.get(
                findNodeOf(
                    graphDraw,
                    EmptyOptionalParameter::class.java
                )
            ).value
        ).isNull()
        assertThat<PresentInGraph>(
            graph.get(
                findNodeOf(
                    graphDraw,
                    NotEmptyValueOfOptional::class.java
                )
            ).value
        ).isNotNull
        assertThat<NotPresentInGraph>(
            graph.get(
                findNodeOf(
                    graphDraw,
                    EmptyValueOfOptional::class.java
                )
            ).value
        ).isNull()
        assertThat<PresentInGraph>(
            graph.get(
                findNodeOf(
                    graphDraw,
                    NotEmptyNullable::class.java
                )
            ).value
        ).isNotNull
        assertThat<NotPresentInGraph>(
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
        val graph = graphDraw.init()
        val value1 = graph[node1]
        val value2 = graph[node2]
        val value3 = graph[node3]
    }

    @Test
    fun appWithAllOfValueOf() {
        val graphDraw = testClass(AppWithAllOfValueOf::class)
        val node1 = graphDraw.nodes[0] as NodeImpl<*>
        val node2 = graphDraw.nodes[1]
        assertThat(node1.dependentNodes).hasSize(1)
        val graph = graphDraw.init()
        val node1Value1 = graph[node1]
        val node2Value1 = graph[node2]
        graph.refresh(node1)
        val node1Value2 = graph[node1]
        val node2Value2 = graph[node2]
        assertThat(node1Value1).isNotSameAs(node1Value2)
        assertThat(node2Value1).isSameAs(node2Value2)
    }

    @Test
    fun appWithAllOf() {
        val graphDraw = testClass(AppWithAllOfComponents::class)
        assertThat(graphDraw.nodes).hasSize(12)
        val graph = graphDraw.init()
        val classWithNonTaggedAllOf = findNodesOf(
            graphDraw,
            AppWithAllOfComponents.ClassWithAllOf::class.java,
            AppWithAllOfComponents.Superclass::class.java
        )
        assertThat(classWithNonTaggedAllOf).hasSize(1)
        val l1 = graph[classWithNonTaggedAllOf[0]]
        assertThat(l1.allOfSuperclass).hasSize(1)
        val classWithTaggedAllOf = findNodesOf(
            graphDraw,
            AppWithAllOfComponents.ClassWithAllOf::class.java,
            AppWithAllOfComponents.Superclass::class.java,
            AppWithAllOfComponents.Superclass::class.java
        )
        assertThat(classWithTaggedAllOf).hasSize(1)
        val l2 = graph[classWithTaggedAllOf[0]]
        assertThat(l2.allOfSuperclass).hasSize(1)
        val classWithAllOfNodesProxies =
            findNodesOf(graphDraw, AppWithAllOfComponents.ClassWithAllValueOf::class.java)
        assertThat(classWithAllOfNodesProxies).hasSize(1)
        val lp = graph[classWithAllOfNodesProxies[0]]
        assertThat(lp.allOfSuperclass).hasSize(5)
        val classWithInterfaces =
            findNodesOf(graphDraw, AppWithAllOfComponents.ClassWithInterfaces::class.java)
        assertThat(classWithInterfaces).hasSize(1)
        val li = graph[classWithInterfaces[0]]
        assertThat(li.allSomeInterfaces).hasSize(2)
        val classWithInterfacesValueOf =
            findNodesOf(graphDraw, AppWithAllOfComponents.ClassWithInterfacesValueOf::class.java)
        assertThat(classWithInterfacesValueOf).hasSize(1)
        val lpi = graph[classWithInterfacesValueOf[0]]
        assertThat(lpi.allSomeInterfaces).hasSize(2)
        val classWithAllOfAnyTag =
            findNodesOf(graphDraw, AppWithAllOfComponents.ClassWithAllOfAnyTag::class.java)
        assertThat(classWithAllOfAnyTag).hasSize(1)
        val aoat = graph[classWithAllOfAnyTag[0]]
        assertThat(aoat.class5All).hasSize(2)
    }

    @Test
    fun unresolvedDependency() {
        Assertions.assertThatThrownBy { testClass(AppWithUnresolvedDependency::class) }
            .isInstanceOfSatisfying(CompilationErrorException::class.java) { e ->
                SoftAssertions.assertSoftly { s: SoftAssertions ->
                    s.assertThat(e.messages)
                        .anyMatch { it.contains("Required dependency type wasn't found and can't be auto created: ru.tinkoff.kora.kora.app.ksp.app.AppWithUnresolvedDependency.Class3") }
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
        assertThat(graphDraw.nodes).hasSize(3)
        val materializedGraph = graphDraw.init()
        assertThat(materializedGraph).isNotNull
    }

    @Test
    fun appWithFactory() {
        testClass(AppWithFactories1::class).init()

        testClass(AppWithFactories2::class).init()

        testClass(AppWithFactories3::class).init()

        testClass(AppWithFactories4::class).init()

//        testClass(AppWithFactories5::class).init() delete or fix?

        Assertions.assertThatThrownBy { testClass(AppWithFactories6::class) }
            .isInstanceOfSatisfying(CompilationErrorException::class.java) { e ->
                SoftAssertions.assertSoftly { s: SoftAssertions ->
                    s.assertThat(e.messages).anyMatch { it.contains("There's a cycle in graph: ") }
                }
            }

        testClass(AppWithFactories7::class).init()

        testClass(AppWithFactories8::class).init()

        testClass(AppWithFactories9::class).init()
        Assertions.assertThatThrownBy { testClass(AppWithFactories10::class) }
            .isInstanceOfSatisfying(CompilationErrorException::class.java) { e ->
                SoftAssertions.assertSoftly { s: SoftAssertions ->
                    s.assertThat(e.messages).anyMatch {
                        it.contains("Required dependency type wasn't found and can't be auto created: java.io.Closeable")
                    }
                }
            }

//        Assertions.assertThatThrownBy { testClass(AppWithFactories11::class) }
//            .isInstanceOfSatisfying(CompilationErrorException::class.java) { e ->
//                SoftAssertions.assertSoftly { s: SoftAssertions ->
//                    s.assertThat(e.messages).contains(
//                        "Required dependency wasn't found and candidate class ru.tinkoff.kora.kora.app.ksp.app.AppWithFactories11.GenericClass<kotlin.String> is not final"
//                    )
//                }
//            } delete or fix?

        testClass(AppWithFactories12::class).init()
    }

    @Test
    fun appWithFactoryWithIntersectedGenerics() {
        testClass(AppWithFactories13::class).init()
    }

    @Test
    fun extensionShouldHandleAnnotationsItProvidesAnnotationProcessorFor() {
        val graphDraw = testClass(AppWithProcessorExtension::class, listOf(AppWithProcessorExtension.TestProcessorProvider()))
        assertThat(graphDraw.nodes).hasSize(2)
        val materializedGraph = graphDraw.init()
        assertThat(materializedGraph).isNotNull
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
        assertThat(graphDraw.nodes).hasSize(12)
        val graph = graphDraw.init()
        assertThat(graph).isNotNull
        val nonTaggedClass3 = findNodesOf(graphDraw, AppWithMultipleTags.Class3::class.java)
        assertThat(nonTaggedClass3).hasSize(1)
        val anyTaggedClass3 = findNodesOf(graphDraw, AppWithMultipleTags.Class3::class.java, AppWithMultipleTags::class.java) as List<NodeImpl<AppWithMultipleTags.Class3>>
        assertThat(anyTaggedClass3).hasSize(1)
        assertThat(graph[anyTaggedClass3[0]].class1s).hasSize(4)
        val tag1TaggedClass3 =
            findNodesOf(graphDraw, AppWithMultipleTags.Class3::class.java, AppWithMultipleTags.Tag1::class.java)
        assertThat(tag1TaggedClass3).hasSize(1)
        assertThat(graph[tag1TaggedClass3[0]].class1s).hasSize(1)
        val tag2Tag3Taggedlass3 = findNodesOf(
            graphDraw,
            AppWithMultipleTags.Class3::class.java,
            AppWithMultipleTags.Tag2::class.java,
            AppWithMultipleTags.Tag3::class.java
        )
        assertThat(tag2Tag3Taggedlass3).hasSize(1)
        assertThat(graph[tag2Tag3Taggedlass3[0]].class1s).hasSize(2)
        val tag4TaggedClass3 = findNodesOf(graphDraw, AppWithMultipleTags.Class3::class.java, AppWithMultipleTags.Tag4::class.java)
        assertThat(tag4TaggedClass3).hasSize(1)
        assertThat(graph[tag4TaggedClass3[0]].class1s).hasSize(1)
    }

    @Test
    fun appWithNestedClasses() {
        val graphDraw = testClass(AppWithNestedClasses::class)
        assertThat(graphDraw.nodes).hasSize(2)
        val materializedGraph = graphDraw.init()
        assertThat(materializedGraph).isNotNull
    }

    @Test
    fun appWithLazyComponents() {
        val graphDraw = testClass(AppWithLazyComponents::class)
        assertThat(graphDraw.nodes).hasSize(3)
        val materializedGraph = graphDraw.init()
        assertThat(materializedGraph).isNotNull
    }

    @Test
    fun appWithModuleOf() {
        val graphDraw = testClass(AppWithModuleOf::class)
        assertThat(graphDraw.nodes).hasSize(2)
        val materializedGraph = graphDraw.init()
        assertThat(materializedGraph).isNotNull
    }

    @Test
    fun appWithClassWithComponentOf() {
        val graphDraw = testClass(AppWithClassWithComponentOf::class)
        assertThat(graphDraw.nodes).hasSize(5)
        val materializedGraph = graphDraw.init()
        assertThat(materializedGraph).isNotNull
    }

    @Test
    fun appWithPromiseOf() {
        val graphDraw = testClass(AppWithPromiseOf::class)
        assertThat(graphDraw.nodes).hasSize(5)
        val materializedGraph = graphDraw.init()
        assertThat(materializedGraph).isNotNull
        materializedGraph.release()
    }

    @Test
    fun appWithOverridenModule() {
        val graphDraw = testClass(AppWithOverridenModule::class)
        assertThat(graphDraw.nodes).hasSize(2)
        val materializedGraph = graphDraw.init()
        assertThat(materializedGraph).isNotNull
        materializedGraph.release()
    }

    @Test
    fun appWithExactDependencyMatch() {
        val graphDraw = testClass(AppWithExactMatch::class)
        assertThat(graphDraw.nodes).hasSize(8)
    }

    @Test
    fun appWithCycleProxy() {
        val graphDraw = testClass(AppWithCycleProxy::class)
        assertThat(graphDraw.nodes).hasSize(7)
        val graph = graphDraw.init();
        assertThat(graph).isNotNull;
    }

    @Test
    fun appPart() {
        val classLoader: ClassLoader = symbolProcess(AppWithAppPart::class, KoraAppProcessorProvider())
        val clazz = classLoader.loadClass(AppWithAppPart::class.java.name + "SubmoduleImpl")
        assertThat(clazz).isNotNull
            .isInterface
            .hasMethods("_component0", "_component1")
            .matches { cls -> !AppWithAppPart.Module::class.java.isAssignableFrom(cls) }

        val targetFile1 = "build/in-test-generated-ksp/sources/" + AppWithAppPart::class.java.name.replace('.', '/') + "SubmoduleImpl.kt"
        val targetFile2 = "src/test/kotlin/" + AppWithAppPartApp::class.java.name.replace('.', '/') + ".kt"
        val classLoaderApp = symbolProcessFiles(listOf(targetFile1, targetFile2))
        val appClazz = classLoaderApp.loadClass(AppWithAppPartApp::class.java.name + "Graph")
        assertThat(appClazz).isNotNull
    }

    @Test
    fun appPartAndAppSubmodule() {
        val classLoader: ClassLoader = symbolProcess(AppWithAppPart::class, listOf(ProcessorOptions.SUBMODULE_GENERATION))
        val clazz = classLoader.loadClass(AppWithAppPart::class.java.name + "SubmoduleImpl")
        assertThat(clazz).isNotNull
            .isInterface
            .hasMethods("_component0", "_component1")
            .matches { cls -> !AppWithAppPart.Module::class.java.isAssignableFrom(cls) }

        val targetFile1 = "build/in-test-generated-ksp/sources/" + AppWithAppPart::class.java.name.replace('.', '/') + "SubmoduleImpl.kt"
        val targetFile2 = "src/test/kotlin/" + AppWithAppPartApp::class.java.name.replace('.', '/') + ".kt"
        val classLoaderApp = symbolProcessFiles(listOf(targetFile1, targetFile2), listOf(ProcessorOptions.SUBMODULE_GENERATION))
        val appClazz = classLoaderApp.loadClass(AppWithAppPartApp::class.java.name + "Graph")
        assertThat(appClazz).isNotNull
        val appClazzSubmodule = classLoaderApp.loadClass(AppWithAppPartApp::class.java.name + "SubmoduleImpl")
        assertThat(appClazzSubmodule).isNotNull
    }

    private fun <T> findNodeOf(graphDraw: ApplicationGraphDraw, type: Class<T>, vararg tags: Class<*>): NodeImpl<T> {
        val nodes = findNodesOf(graphDraw, type, *tags)
        check(nodes.size == 1)
        return nodes[0]
    }

    private fun <T> findNodesOf(graphDraw: ApplicationGraphDraw, type: Class<T>, vararg tags: Class<*>): List<NodeImpl<T>> {
        val graph = graphDraw.init()
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
            }.map { it as NodeImpl<T> }
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


