package io.koraframework.config.symbol.processor

import io.koraframework.config.common.extractor.ConfigValueExtractor
import io.koraframework.config.ksp.processor.ConfigParserSymbolProcessorProvider
import io.koraframework.config.ksp.processor.ConfigSourceSymbolProcessorProvider
import io.koraframework.ksp.common.AbstractSymbolProcessorTest
import org.intellij.lang.annotations.Language

abstract class AbstractConfigTest : AbstractSymbolProcessorTest() {
    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.config.common.annotation.*;
        """.trimIndent()
    }

    protected open fun compileConfig(arguments: List<*>, @Language("kotlin") vararg sources: String): ConfigValueExtractor<Any?> {
        super.compile0(listOf(ConfigSourceSymbolProcessorProvider(), ConfigParserSymbolProcessorProvider()), *sources)
        compileResult.assertSuccess()
        return loadClass("\$TestConfig_ConfigValueExtractor")
            .constructors[0]
            .newInstance(*arguments.map { if (it is GeneratedObject<*>) it() else it }.toTypedArray()) as ConfigValueExtractor<Any?>
    }
}
