package ru.tinkoff.kora.resilient.circuitbreaker

import com.google.devtools.ksp.KspExperimental
import org.awaitility.Awaitility.*
import org.awaitility.core.ConditionFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.util.concurrent.Callable
import java.util.function.Supplier

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class CircuitBreakerTests {

    private val WAIT_IN_OPEN: Duration = Duration.ofMillis(50)

    private fun awaitily(): ConditionFactory {
        return await().atMost(Duration.ofMillis(150)).pollDelay(Duration.ofMillis(5))
    }

    @Test
    fun switchFromClosedToOpenToHalfOpenToOpenToHalfOpenToClosedForAccept() {
        // given
        val config: CircuitBreakerConfig.NamedConfig = `$CircuitBreakerConfig_NamedConfig_ConfigValueExtractor`.NamedConfig_Impl(
            true, 50, WAIT_IN_OPEN, 2, 4L, 2L, KoraCircuitBreakerPredicate::class.java.canonicalName
        )
        val circuitBreaker = KoraCircuitBreaker("default", config, KoraCircuitBreakerPredicate(), NoopCircuitBreakerMetrics())

        val successCallable = Callable {
            try {
                circuitBreaker.accept { "success" } != null
            } catch (e: CallNotPermittedException) {
                false
            }
        }
        val failSupplier = Supplier<Any?> {
            check(!true)
            null
        }

        // when
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
        assertThrows<IllegalStateException> { circuitBreaker.accept(failSupplier) }
        assertThrows<IllegalStateException> { circuitBreaker.accept(failSupplier) }

        assertThrows<CallNotPermittedException> { circuitBreaker.accept(failSupplier) } // closed switched to open
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)

        awaitily().until(successCallable) // open switched to half open + 1 acquire
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.state)

        assertThrows<IllegalStateException> { circuitBreaker.accept(failSupplier) } // half open switched to open
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.state)

        // then
        awaitily().until(successCallable) // open switched to half open + 1 acquire
        assertEquals(CircuitBreaker.State.HALF_OPEN, circuitBreaker.state)
        awaitily().until(successCallable) // half open switched to CLOSED
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.state)
    }
}
