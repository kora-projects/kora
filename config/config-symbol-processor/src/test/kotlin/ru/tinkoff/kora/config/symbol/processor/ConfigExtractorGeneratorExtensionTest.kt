@file:OptIn(KspExperimental::class)
@file:Suppress("UNCHECKED_CAST")

package ru.tinkoff.kora.config.symbol.processor

import com.google.devtools.ksp.KspExperimental
import com.typesafe.config.Config
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw
import ru.tinkoff.kora.application.graph.Node
import ru.tinkoff.kora.application.graph.RefreshableGraph
import ru.tinkoff.kora.application.graph.Wrapped
import ru.tinkoff.kora.config.ksp.processor.ConfigRootSymbolProcessorProvider
import ru.tinkoff.kora.config.ksp.processor.ConfigSourceSymbolProcessorProvider
import ru.tinkoff.kora.config.symbol.processor.cases.*
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.symbolProcess
import java.lang.reflect.Constructor
import java.util.*
import java.util.function.Supplier
import kotlin.reflect.KClass

@KspExperimental
internal class ConfigExtractorGeneratorExtensionTest {

    @Test
    fun extensionTest() {
        val graphDraw = createGraphDraw(AppWithConfig::class)
        val graph: RefreshableGraph = graphDraw.init().block()!!
        val values: List<*> = graphDraw.nodes
            .map { n: Node<*>? -> graph.get(n) }
            .toList()
        val props = Properties()
        props["foo.bar.baz1"] = 1
        props["foo.bar.baz2"] = true
        props["foo.bar.baz3"] = "asd"
        props["foo.bar.baz4"] = listOf(1, false, "zxc")
        val classConfig = values.filter { obj: Any? -> ClassConfig::class.java.isInstance(obj) }
            .map { obj: Any? -> ClassConfig::class.java.cast(obj) }.firstOrNull()
        Assertions.assertNotNull(classConfig)
        Assertions.assertEquals(
            classConfig, ClassConfig(
                intField = 1,
                boxedIntField = 2,
                longField = 3L,
                boxedLongField = 4L,
                doubleField = 5.0,
                boxedDoubleField = 6.0,
                booleanField = true,
                boxedBooleanField = false,
                stringField = "some string value",
                listField = listOf(1, 2, 3, 4, 5),
                objectField = SomeConfig(1, "baz"),
                props = props
            )
        )
        val recordConfig = values.filter { obj: Any? -> DataClassConfig::class.java.isInstance(obj) }
            .map { obj: Any? -> DataClassConfig::class.java.cast(obj) }.firstOrNull()
        Assertions.assertNotNull(recordConfig)
        Assertions.assertEquals(
            recordConfig, DataClassConfig(
                intField = 1,
                boxedIntField = 2,
                longField = 3L,
                boxedLongField = 4L,
                doubleField = 5.0,
                boxedDoubleField = 6.0,
                booleanField = true,
                boxedBooleanField = false,
                stringField = "some string value",
                listField = listOf(1, 2, 3, 4, 5),
                objectField = SomeConfig(1, "baz"),
                props = props
            )
        )
    }

    @Test
    fun extensionTestWithComponentOf() {
        val graphDraw: ApplicationGraphDraw = createGraphDraw(AppWithConfigWithModule::class, PojoConfigRootWithComponentOf::class)
        val graph: RefreshableGraph = graphDraw.init().block()!!
        val values: List<*> = graphDraw.nodes
            .map { n: Node<*>? -> graph.get(n) }
            .toList()
        assertThat(values).hasSize(12)
        assertThat(values.stream().anyMatch { o: Any? -> o is MockLifecycle }).isTrue
        assertThat(values.stream().anyMatch { o: Any? -> o is ClassConfig }).isTrue
        assertThat(values.stream().anyMatch { o: Any? -> o is DataClassConfig }).isTrue
    }

    @Test
    fun appWithConfigSource() {
        val graphDraw: ApplicationGraphDraw = createGraphDraw(AppWithConfigSource::class)
        val graph: RefreshableGraph = graphDraw.init().block()!!
        val values: List<*> = graphDraw.nodes
            .map { n: Node<*>? -> graph.get(n) }
            .toList()
        assertThat(values).hasSize(4)
        assertThat(values.stream().anyMatch { o: Any? -> o is MockLifecycle }).isTrue
        assertThat(
            values.stream().anyMatch { o: Any? -> o is Wrapped<*> && o.value() is Config }).isTrue
        val config = values
            .filterIsInstance<AppWithConfigSource.SomeConfig>()
            .map { obj: Any? -> AppWithConfigSource.SomeConfig::class.java.cast(obj) }
            .first()

        assertThat(config)
            .isNotNull
            .isEqualTo(AppWithConfigSource.SomeConfig( "field", 42))
    }

    fun createGraphDraw(vararg targetClasses: KClass<*>): ApplicationGraphDraw {
        return try {
            val classLoader = symbolProcess(
                targetClasses.toList(),
                KoraAppProcessorProvider(),
                ConfigRootSymbolProcessorProvider(),
                ConfigSourceSymbolProcessorProvider()
            )
            val targetClass = targetClasses.first { it.simpleName!!.contains("App") }
            val clazz = classLoader.loadClass(targetClass.qualifiedName + "Graph")
            val constructors: Array<Constructor<out Supplier<out ApplicationGraphDraw>?>> =
                clazz.constructors as Array<Constructor<out Supplier<out ApplicationGraphDraw>?>>
            constructors[0].newInstance()!!.get()
        } catch (e: Exception) {
            if (e.cause != null) {
                throw (e.cause as Exception?)!!
            }
            throw e
        }
    }
}
