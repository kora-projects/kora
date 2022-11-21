package ru.tinkoff.kora.resilient.symbol.processor.aop

import com.google.devtools.ksp.KspExperimental
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.CircuitBreakerFallbackTarget
import ru.tinkoff.kora.resilient.symbol.processor.aop.testdata.CircuitBreakerLifecycle

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class CircuitBreakerFallbackTests : CircuitBreakerRunner() {

    private fun getService(graph: InitializedGraph): CircuitBreakerFallbackTarget {
        val values = graph.graphDraw.nodes
            .stream()
            .map { node -> graph.refreshableGraph.get(node) }
            .toList()

        return values.asSequence()
            .filter { a -> a is CircuitBreakerLifecycle }
            .map { a -> (a as CircuitBreakerLifecycle).targetFallback }
            .first()
    }

    @Test
    fun syncCircuitBreaker() {
        // given
        val graphDraw = createGraphDraw()
        val service = getService(graphDraw)

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
        val graphDraw = createGraphDraw()
        val service = getService(graphDraw)

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
        val graphDraw = createGraphDraw()
        val service = getService(graphDraw)

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
