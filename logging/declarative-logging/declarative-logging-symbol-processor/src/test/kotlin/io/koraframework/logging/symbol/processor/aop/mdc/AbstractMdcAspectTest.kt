package io.koraframework.logging.symbol.processor.aop.mdc

import io.koraframework.ksp.common.AbstractSymbolProcessorTest

abstract class AbstractMdcAspectTest : AbstractSymbolProcessorTest() {

    override fun commonImports(): String {
        return super.commonImports() + """
            import io.koraframework.logging.common.annotation.Mdc
            import io.koraframework.logging.common.MDC
            import io.koraframework.logging.symbol.processor.aop.mdc.MDCContextHolder
            import java.util.concurrent.CompletionStage
            import java.util.concurrent.CompletableFuture
            """.trimIndent()
    }
}
