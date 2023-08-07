package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class CircuitBreakerTests : AppRunner() {

    private inline fun <reified T> getService(): T {
        val graph = getGraphForApp(
            AppWithConfig::class,
            listOf(
                CircuitBreakerTarget::class,
                FallbackTarget::class,
                RetryTarget::class,
                TimeoutTarget::class,
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

    @Test
    fun suspendCircuitBreaker() {
        // given
        val service = getService<CircuitBreakerTarget>()

        // when
        try {
            runBlocking { service.getValueSuspend() }
            fail("Should not happen")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
        }

        // then
        try {
            runBlocking { service.getValueSuspend() }
            fail("Should not happen")
        } catch (ex: CallNotPermittedException) {
            assertNotNull(ex.message)
        }
    }

    @Test
    fun flowCircuitBreaker() {
        // given
        val service = getService<CircuitBreakerTarget>()

        // when
        try {
            runBlocking { service.getValueFLow().collect { v -> v } }
            fail("Should not happen")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
        }

        // then
        try {
            runBlocking { service.getValueFLow().collect { v -> v } }
            fail("Should not happen")
        } catch (ex: CallNotPermittedException) {
            assertNotNull(ex.message)
        }
    }
}
