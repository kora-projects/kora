package ru.tinkoff.kora.config.symbol.processor

import org.intellij.lang.annotations.Language
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor
import ru.tinkoff.kora.config.ksp.processor.ConfigParserSymbolProcessorProvider
import ru.tinkoff.kora.config.ksp.processor.ConfigSourceSymbolProcessorProvider
import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest

abstract class AbstractConfigTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.config.common.annotation.*;
        """.trimIndent()
    }

    protected open fun compileConfig(arguments: List<*>, @Language("kotlin") vararg sources: String): ConfigValueExtractor<Any?> {
        super.compile(listOf(ConfigSourceSymbolProcessorProvider(), ConfigParserSymbolProcessorProvider()), *sources)
        compileResult.assertSuccess()
        return compileResult.loadClass("\$TestConfig_ConfigValueExtractor")
            .constructors[0]
            .newInstance(*arguments.map { if (it is GeneratedObject<*>) it() else it }.toTypedArray()) as ConfigValueExtractor<Any?>
    }
}
