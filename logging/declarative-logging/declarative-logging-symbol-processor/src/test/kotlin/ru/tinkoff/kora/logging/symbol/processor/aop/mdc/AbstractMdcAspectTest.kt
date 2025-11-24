package ru.tinkoff.kora.logging.symbol.processor.aop.mdc

import ru.tinkoff.kora.ksp.common.AbstractSymbolProcessorTest

abstract class AbstractMdcAspectTest : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import ru.tinkoff.kora.logging.common.annotation.Mdc
            import ru.tinkoff.kora.logging.common.MDC
            import ru.tinkoff.kora.logging.symbol.processor.aop.mdc.MDCContextHolder
            import java.util.concurrent.CompletionStage
            import java.util.concurrent.CompletableFuture
            """.trimIndent()
    }
}
