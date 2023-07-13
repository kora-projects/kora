package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import org.junit.jupiter.api.Assertions
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw
import ru.tinkoff.kora.application.graph.RefreshableGraph
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.symbolProcess
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.AppWithConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier
import kotlin.reflect.KClass

@KspExperimental
open class TestAppRunner : Assertions() {

    private data class ClassLoaderArguments(val processors: List<KClass<out SymbolProcessorProvider>>, val classes: List<KClass<*>>)

    companion object {
        private val classLoaderMap: MutableMap<ClassLoaderArguments, ClassLoader> = ConcurrentHashMap<ClassLoaderArguments, ClassLoader>()
    }

    data class InitializedGraph(val graphDraw: ApplicationGraphDraw, val refreshableGraph: RefreshableGraph)

    inline fun <reified T> getServiceFromGraph(): T {
        return getServiceFromGraph(AppWithConfig::class)
    }

    inline fun <reified T> getServiceFromGraph(app: KClass<*>): T {
        val pair: Pair<T, T> = getServicesFromGraph(app)
        return pair.first
    }

    inline fun <reified T, reified V> getServicesFromGraph(): Pair<T, V> {
        return getServicesFromGraph(AppWithConfig::class)
    }

    inline fun <reified T, reified V> getServicesFromGraph(app: KClass<*>): Pair<T, V> {
        val graph = getGraphForApp(app, listOf(T::class, V::class))
        val values = graph.graphDraw.nodes
            .stream()
            .map { node -> graph.refreshableGraph.get(node) }
            .toList()

        val t = values.asSequence()
            .filter { a -> a is T }
            .map { a -> a as T }
            .first()

        val v = values.asSequence()
            .filter { a -> a is V }
            .map { a -> a as V }
            .first()

        return Pair(t, v)
    }

    fun getProcessors(): List<SymbolProcessorProvider> {
        return listOf(
            KoraAppProcessorProvider(),
            AopSymbolProcessorProvider()
        )
    }

    private fun getClassLoader(processors: List<SymbolProcessorProvider>, classes: List<KClass<*>>): ClassLoader {
        val arguments = ClassLoaderArguments(processors.map { it::class }.toList(), classes)
        val classLoaderSaved = classLoaderMap[arguments]
        if (classLoaderSaved != null) {
            return classLoaderSaved
        }

        val classLoader = symbolProcess(classes, processors)
        classLoaderMap[arguments] = classLoader
        return classLoader
    }

    fun getGraphForClasses(targetClasses: List<KClass<*>>): InitializedGraph {
        return getGraphForApp(AppWithConfig::class, targetClasses)
    }

    fun getGraphForApp(app: KClass<*>, targetClasses: List<KClass<*>>): InitializedGraph {
        return try {
            val classes = targetClasses.toMutableList()
            classes.add(app)
            val classLoader = getClassLoader(getProcessors(), classes)
            val clazz = classLoader.loadClass(app.qualifiedName + "Graph")
            val graphDraw = (clazz.constructors.first().newInstance() as Supplier<ApplicationGraphDraw>).get()
            val graph = graphDraw.init().block()!!
            InitializedGraph(graphDraw, graph)
        } catch (e: Exception) {
            throw e
        }
    }
}
