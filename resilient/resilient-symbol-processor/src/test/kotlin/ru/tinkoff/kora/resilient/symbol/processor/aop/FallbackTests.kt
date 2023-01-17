package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.aop.symbol.processor.AopSymbolProcessorProvider
import ru.tinkoff.kora.ksp.common.CompilationErrorException
import ru.tinkoff.kora.ksp.common.symbolProcess
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class FallbackTests : AppRunner() {

    private inline fun <reified T> getService(): T {
        val graph = getGraphForApp(
            AppWithConfig::class,
            listOf(
                CircuitBreakerTarget::class,
                FallbackTarget::class,
                RetryableTarget::class,
                TimeoutTarget::class,
            )
        )

        return getServiceFromGraph(graph)
    }

    @Test
    fun incorrectArgumentFallback() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(FallbackIllegalArgumentTarget::class, AopSymbolProcessorProvider()) }
    }

    @Test
    fun incorrectSignatureFallback() {
        assertThrows(
            CompilationErrorException::class.java
        ) { symbolProcess(FallbackIllegalSignatureTarget::class, AopSymbolProcessorProvider()) }
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
    fun monoFallback() {
        // given
        val service = getService<FallbackTarget>()
        service.alwaysFail = false

        // when
        assertEquals(FallbackTarget.VALUE, runBlocking { service.getValueSuspend() })
        service.alwaysFail = true

        // then
        assertEquals(FallbackTarget.FALLBACK, runBlocking { service.getValueSuspend() })
    }

    @Test
    fun fluxFallback() {
        // given
        val service = getService<FallbackTarget>()
        service.alwaysFail = false

        // when
        assertEquals(FallbackTarget.VALUE, runBlocking { service.getValueFLow().first() })
        service.alwaysFail = true

        // then
        assertEquals(FallbackTarget.FALLBACK, runBlocking { service.getValueFLow().first() })
    }
}
