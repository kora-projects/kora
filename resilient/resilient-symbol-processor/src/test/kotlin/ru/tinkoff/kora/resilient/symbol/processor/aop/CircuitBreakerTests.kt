package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.AppWithConfig
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.CircuitBreakerTarget
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.`typealias`.CircuitBreakerAliasTarget

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class CircuitBreakerTests : AppRunner() {

    private inline fun <reified T> getService(): T {
        val graph = getGraphForApp(
            AppWithConfig::class,
            listOf(
                CircuitBreakerTarget::class,
                CircuitBreakerAliasTarget::class,
            )
        )

        return getServiceFromGraph(graph)
    }

    @Test
    fun syncCircuitBreaker() {
        // given
        val service = getService<CircuitBreakerTarget>()

        // when
        try {
            service.getValueSync()
            fail("Should not happen")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
        }

        // then
        try {
            service.getValueSync()
            fail("Should not happen")
        } catch (ex: CallNotPermittedException) {
            assertNotNull(ex.message)
        }
    }

    @Test
    fun aliasAnnotation() {
        // given
        val service = getService<CircuitBreakerAliasTarget>()

        // then
        assertThrows<IllegalStateException> {
            service.getValueSync()
        }

        // then
        assertThrows<CallNotPermittedException> {
            service.getValueSync()
        }
    }

    @Test
    fun voidCircuitBreaker() {
        // given
        val service = getService<CircuitBreakerTarget>()

        // when
        try {
            service.getValueSyncVoid()
            fail("Should not happen")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
        }

        // then
        try {
            service.getValueSyncVoid()
            fail("Should not happen")
        } catch (ex: CallNotPermittedException) {
            assertNotNull(ex.message)
        }
    }
}
