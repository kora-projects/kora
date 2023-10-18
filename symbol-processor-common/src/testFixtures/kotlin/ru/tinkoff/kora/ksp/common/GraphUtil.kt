package ru.tinkoff.kora.ksp.common

import ru.tinkoff.kora.application.graph.ApplicationGraphDraw
import java.util.function.Supplier

object GraphUtil {
    fun Class<*>.toGraph(): GraphContainer {
        val draw = toGraphDraw()
        return GraphContainer(draw)
    }

    fun Class<*>.toGraphDraw(): ApplicationGraphDraw {
        require(Supplier::class.java.isAssignableFrom(this))
        val supplier = this.constructors[0].newInstance()
        require(supplier is Supplier<*>)
        val draw = supplier.get()
        require(draw is ApplicationGraphDraw)
        return draw
    }

    class GraphContainer(val draw: ApplicationGraphDraw) : AutoCloseable {
        val graph = draw.init()!!

        fun <T : Any> findByType(type: Class<T>) = draw.nodes.asSequence()
            .map { graph.get(it) }
            .filterIsInstance(type)
            .firstOrNull()

        fun <T : Any> findAllByType(type: Class<T>) = draw.nodes.asSequence()
            .map { graph.get(it) }
            .filterIsInstance(type)
            .toList()

        override fun close() {
            graph.release()
        }
    }
}
