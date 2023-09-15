package ru.tinkoff.kora.kora.app.ksp

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import java.util.function.Supplier

class KoraBigGraphTest : AbstractSymbolProcessorTest() {
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
        val compileResult = compile(listOf(KoraAppProcessorProvider()), sb.toString())
        if (compileResult.isFailed()) {
            throw compileResult.compilationException()
        }

        val appClass = compileResult.loadClass("ExampleApplicationGraph")
        val `object` = appClass.getConstructor().newInstance() as Supplier<ApplicationGraphDraw>
        val draw = `object`.get()
        Assertions.assertThat(draw.nodes).hasSize(1500)
        draw.init()
    }
}
