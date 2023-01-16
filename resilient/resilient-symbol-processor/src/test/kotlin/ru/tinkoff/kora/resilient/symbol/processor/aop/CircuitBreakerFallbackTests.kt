package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.CircuitBreakerTarget
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.CircuitBreakerFallbackTarget

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class CircuitBreakerFallbackTests : TestAppRunner() {

    @Test
    fun syncCircuitBreaker() {
        // given
        val services: Pair<CircuitBreakerTarget, CircuitBreakerFallbackTarget> = getServicesFromGraph()
        val service = services.second

        // when
        try {
            service.getValueSync()
            fail("Should not happen")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
        }

        // then
        val fallback = service.getValueSync()
        assertEquals(CircuitBreakerFallbackTarget.FALLBACK, fallback)
    }

    @Test
    fun suspendCircuitBreaker() {
        // given
        val services: Pair<CircuitBreakerTarget, CircuitBreakerFallbackTarget> = getServicesFromGraph()
        val service = services.second

        // when
        try {
            runBlocking { service.getValueSuspend() }
            fail("Should not happen")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
        }

        // then
        val fallback = runBlocking { service.getValueSuspend() }
        assertEquals(CircuitBreakerFallbackTarget.FALLBACK, fallback)
    }

    @Test
    fun flowCircuitBreaker() {
        // given
        val services: Pair<CircuitBreakerTarget, CircuitBreakerFallbackTarget> = getServicesFromGraph()
        val service = services.second

        // when
        try {
            runBlocking { service.getValueFLow().toList() }
            fail("Should not happen")
        } catch (e: IllegalStateException) {
            assertNotNull(e.message)
        }

        // then
        val fallback = runBlocking { service.getValueFLow().toList() }
        assertEquals(1, fallback.size)
        assertEquals(CircuitBreakerFallbackTarget.FALLBACK, fallback[0])
    }
}
