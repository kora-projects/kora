package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Assertions
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw
import ru.tinkoff.kora.application.graph.RefreshableGraph
import ru.tinkoff.kora.config.ksp.processor.ConfigRootSymbolProcessorProvider
import ru.tinkoff.kora.config.ksp.processor.ConfigSourceSymbolProcessorProvider
import ru.tinkoff.kora.kora.app.ksp.KoraAppProcessorProvider
import ru.tinkoff.kora.ksp.common.symbolProcess
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.AppWithConfig
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.CircuitBreakerFallbackTarget
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.CircuitBreakerTarget
import java.util.function.Supplier
import kotlin.reflect.KClass

@KspExperimental
open class CircuitBreakerRunner : Assertions() {

    companion object {
        var GRAPH: InitializedGraph? = null
    }

    data class InitializedGraph(val graphDraw: ApplicationGraphDraw, val refreshableGraph: RefreshableGraph)

    fun createGraphDraw(): InitializedGraph {
        if (GRAPH == null) {
            GRAPH = createGraphDraw(
                AppWithConfig::class,
                CircuitBreakerTarget::class,
                CircuitBreakerFallbackTarget::class
            )
        }

        return GRAPH!!
    }

    fun createGraphDraw(app: KClass<*>, vararg targetClasses: KClass<*>): InitializedGraph {
        return try {
            val classes = targetClasses.toMutableList()
            classes.add(app)
            val classLoader = symbolProcess(
                classes,
                KoraAppProcessorProvider(),
                AopSymbolProcessorProvider(),
                ConfigRootSymbolProcessorProvider(),
                ConfigSourceSymbolProcessorProvider()
            )
            val clazz = classLoader.loadClass(app.qualifiedName + "Graph")
            val graphDraw = (clazz.constructors.first().newInstance() as Supplier<ApplicationGraphDraw>).get()
            val graph = graphDraw.init().block()!!
            InitializedGraph(graphDraw, graph)
        } catch (e: Exception) {
            if (e.cause != null) {
                throw IllegalStateException(e.cause)
            }
            throw IllegalStateException(e)
        }
    }
}
