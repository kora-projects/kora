package ru.tinkoff.kora.kora.app.ksp

import org.intellij.lang.annotations.Language
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.application.graph.ApplicationGraphDraw
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest
import ru.tinkoff.kora.ksp.common.KotlinCompilation
import java.util.function.Supplier

abstract class AbstractKoraAppProcessorTest : AbstractSymbolProcessorTest() {
    override fun commonImports() = super.commonImports() + """
        import ru.tinkoff.kora.application.graph.*;
        import java.util.Optional;
        
        """.trimIndent()


    protected fun compile(@Language("kotlin") vararg sources: String): ApplicationGraphDraw {
        KotlinCompilation()
            .withPartialClasspath()
            .compile(listOf(KoraAppProcessorProvider(), AopSymbolProcessorProvider()), *sources)
            .assertSuccess()

        val appClass = loadClass("ExampleApplicationGraph")
        val `object` = appClass.getConstructor().newInstance() as Supplier<ApplicationGraphDraw>
        return `object`.get()
    }
}
