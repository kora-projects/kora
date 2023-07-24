package ru.tinkoff.kora.kora.app.ksp

import org.intellij.lang.annotations.Language
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import java.util.function.Supplier

abstract class AbstractKoraAppProcessorTest : AbstractSymbolProcessorTest() {
    override fun commonImports() = super.commonImports() + """
        import ru.tinkoff.kora.application.graph.*;
        import java.util.Optional;
        
        """.trimIndent()


    protected fun compile(@Language("kotlin") vararg sources: String): ApplicationGraphDraw {
        val compileResult = compile(listOf(KoraAppProcessorProvider()), *sources)
        if (compileResult.isFailed()) {
            throw compileResult.compilationException()
        }

        val appClass = compileResult.loadClass("ExampleApplicationGraph")
        val `object` = appClass.getConstructor().newInstance() as Supplier<ApplicationGraphDraw>
        return `object`.get()
    }

}
