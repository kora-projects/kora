package io.koraframework.kora.app.ksp

import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.application.graph.ApplicationGraphDraw
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import org.intellij.lang.annotations.Language
import java.util.function.Supplier

abstract class AbstractKoraAppProcessorTest : AbstractSymbolProcessorTest() {
    override fun commonImports() = super.commonImports() + """
        import io.koraframework.application.graph.*;
        import java.util.Optional;
        
        """.trimIndent()


    protected fun compile(@Language("kotlin") vararg sources: String): ApplicationGraphDraw {
        compile0(listOf(KoraAppProcessorProvider(), AopSymbolProcessorProvider()), *sources)
            .assertSuccess()

        val appClass = loadClass("ExampleApplicationGraph")
        val `object` = appClass.getConstructor().newInstance() as Supplier<ApplicationGraphDraw>
        return `object`.get()
    }
}
