package io.koraframework.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import io.koraframework.aop.symbol.processor.AopSymbolProcessorProvider
import io.koraframework.ksp.common.CompilationErrorException
import io.koraframework.ksp.common.symbolProcess
import io.koraframework.resilient.symbol.processor.aop.testdata.AppWithConfig
import io.koraframework.resilient.symbol.processor.aop.testdata.FallbackIllegalArgumentTarget
import io.koraframework.resilient.symbol.processor.aop.testdata.FallbackIllegalSignatureTarget
import io.koraframework.resilient.symbol.processor.aop.testdata.FallbackTarget
import io.koraframework.resilient.symbol.processor.aop.testdata.`typealias`.FallbackAliasTarget

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class FallbackTests : AppRunner() {

    private inline fun <reified T> getService(): T {
        val graph = getGraphForApp(
            AppWithConfig::class,
            listOf(
                FallbackTarget::class,
                FallbackAliasTarget::class,
            )
        )

        return getServiceFromGraph(graph)
    }

    @Test
    fun incorrectArgumentFallback() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(listOf(AopSymbolProcessorProvider()), listOf(FallbackIllegalArgumentTarget::class)) }
    }

    @Test
    fun incorrectSignatureFallback() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(listOf(AopSymbolProcessorProvider()), listOf(FallbackIllegalSignatureTarget::class)) }
    }

    @Test
    fun voidFallback() {
        // given
        val service = getService<FallbackTarget>()
        assertEquals(FallbackTarget.VoidState.NONE, service.voidState)
        service.alwaysFail = false

        // when
        service.voidSync()
        assertEquals(FallbackTarget.VoidState.VALUE, service.voidState)
        service.alwaysFail = true

        // then
        service.voidSync()
        assertEquals(FallbackTarget.VoidState.FALLBACK, service.voidState)
    }

    @Test
    fun syncFallback() {
        // given
        val service = getService<FallbackTarget>()
        service.alwaysFail = false

        // when
        assertEquals(FallbackTarget.VALUE, service.getValueSync())
        service.alwaysFail = true

        // then
        assertEquals(FallbackTarget.FALLBACK, service.getValueSync())
    }

    @Test
    fun aliasAnnotation() {
        // given
        val service = getService<FallbackAliasTarget>()
        service.alwaysFail = false

        // when
        assertEquals(FallbackAliasTarget.VALUE, service.getValueSync())
        service.alwaysFail = true

        // then
        assertEquals(FallbackAliasTarget.FALLBACK, service.getValueSync())
    }
}
