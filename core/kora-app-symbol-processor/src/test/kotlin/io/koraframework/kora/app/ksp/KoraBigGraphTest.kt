package io.koraframework.kora.app.ksp

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import io.koraframework.application.graph.ApplicationGraphDraw
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import java.lang.reflect.ParameterizedType
import java.util.function.Supplier

class KoraBigGraphTest : AbstractSymbolProcessorTest() {
    override fun commonImports() = super.commonImports() + """
        import io.koraframework.application.graph.*;

        """.trimIndent()

    private fun compileGraph(source: String): ApplicationGraphDraw {
        compile0(listOf(KoraAppProcessorProvider()), source)
            .assertSuccess()

        val appClass = loadClass("ExampleApplicationGraph")
        val `object` = appClass.getConstructor().newInstance() as Supplier<ApplicationGraphDraw>
        return `object`.get()
    }

    @Test
    fun test() {
        val sb = StringBuilder("\n")
            .append("@KoraApp\n")
            .append("interface ExampleApplication {\n")
        for (i in 0 until 1500) {
            sb.append("  @Root\n")
            sb.append("  fun component").append(i).append("() = \"\";\n")
        }
        sb.append("}\n")
        val draw = compileGraph(sb.toString())
        Assertions.assertThat(draw.nodes).hasSize(1500)
        draw.init()
    }

    @Test
    fun testWideAll() {
        val items = 2000
        val sb = StringBuilder("\n")
            .append("@KoraApp\n")
            .append("interface ExampleApplication {\n")
            .append("  interface Handler\n")
            .append("  class HandlerImpl : Handler\n")
            .append("  class Consumer(size: Int) { init { check(size == ").append(items).append(") { \"size \" + size } } }\n")
            .append("  @Root\n")
            .append("  fun all(all: All<Handler>) = Consumer(all.count())\n")
            .append("  @Root\n")
            .append("  fun allValues(all: All<ValueOf<Handler>>) = Consumer(all.count())\n")
            .append("  @Root\n")
            .append("  fun allPromises(all: All<PromiseOf<Handler>>) = Consumer(all.count())\n")
        for (i in 0 until items) {
            if (i % 2 == 0) {
                sb.append("  fun handler").append(i).append("(): Handler = object : Handler {}\n")
            } else {
                sb.append("  fun handler").append(i).append("() = HandlerImpl()\n")
            }
        }
        sb.append("}\n")
        val draw = compileGraph(sb.toString())
        Assertions.assertThat(draw.nodes).hasSize(items + 3)
        draw.init()
    }

    @Test
    fun testHolderConstructorCodeSize() {
        // every consumer has 600 node references in its dependency lists, 500 of such components do not fit into one method
        val items = 300
        val consumers = 80
        val sb = StringBuilder("\n")
            .append("@KoraApp\n")
            .append("interface ExampleApplication {\n")
            .append("  interface Handler\n")
            .append("  interface GenericHandler<T> : Handler\n")
            .append("  class Consumer\n")
        for (i in 0 until items) {
            if (i % 2 == 0) {
                sb.append("  fun handler").append(i).append("(): Handler = object : Handler {}\n")
            } else {
                sb.append("  fun handler").append(i).append("(): GenericHandler<List<String>> = object : GenericHandler<List<String>> {}\n")
            }
        }
        for (i in 0 until consumers) {
            sb.append("  @Root\n")
            sb.append("  fun consumer").append(i).append("(all: All<Handler>) = Consumer()\n")
        }
        sb.append("}\n")
        val draw = compileGraph(sb.toString())
        Assertions.assertThat(draw.nodes).hasSize(items + consumers)
        val types = draw.nodes.map { it.type() }
        Assertions.assertThat(types).contains(loadClass("ExampleApplication\$Handler"))
        Assertions.assertThat(types.filterIsInstance<ParameterizedType>())
            .hasSize(items / 2)
            .allSatisfy { Assertions.assertThat(it.typeName).endsWith("ExampleApplication\$GenericHandler<java.util.List<java.lang.String>>") }
        draw.init()
    }

    @Test
    fun testWideAllOfGenericAndWrapped() {
        val items = 300
        val sb = StringBuilder("\n")
            .append("@KoraApp\n")
            .append("interface ExampleApplication {\n")
            .append("  interface Cache<K, V>\n")
            .append("  class StringCache : Cache<String, String>\n")
            .append("  class IntCache : Cache<String, Int>\n")
            .append("  class Consumer(size: Int) { init { check(size == ").append(items).append(") { \"size \" + size } } }\n")
            .append("  @Root\n")
            .append("  fun all(all: All<Cache<String, *>>) = Consumer(all.count())\n")
            .append("  @Root\n")
            .append("  fun allValues(all: All<ValueOf<Cache<String, *>>>) = Consumer(all.count())\n")
        for (i in 0 until items) {
            when (i % 3) {
                0 -> sb.append("  fun cache").append(i).append("() = StringCache()\n")
                1 -> sb.append("  fun cache").append(i).append("() = IntCache()\n")
                else -> sb.append("  fun cache").append(i).append("(): Wrapped<Cache<String, *>> = Wrapped { IntCache() }\n")
            }
        }
        sb.append("}\n")
        val draw = compileGraph(sb.toString())
        Assertions.assertThat(draw.nodes).hasSize(items + 2)
        draw.init()
    }
}
