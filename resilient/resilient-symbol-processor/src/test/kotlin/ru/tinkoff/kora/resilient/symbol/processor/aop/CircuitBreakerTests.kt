package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.resilient.circuitbreaker.CallNotPermittedException
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.CircuitBreakerFallbackTarget
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.CircuitBreakerTarget

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class CircuitBreakerTests : TestAppRunner() {


    @Test
    fun syncCircuitBreaker() {
        // given
        val services: Pair<CircuitBreakerTarget, CircuitBreakerFallbackTarget> = getServicesFromGraph()
        val service = services.first

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
    fun suspendCircuitBreaker() {
        // given
        val services: Pair<CircuitBreakerTarget, CircuitBreakerFallbackTarget> = getServicesFromGraph()
        val service = services.first

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
        val services: Pair<CircuitBreakerTarget, CircuitBreakerFallbackTarget> = getServicesFromGraph()
        val service = services.first

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
