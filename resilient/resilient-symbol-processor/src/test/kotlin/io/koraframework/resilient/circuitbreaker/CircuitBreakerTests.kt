package io.koraframework.resilient.circuitbreaker

import com.google.devtools.ksp.KspExperimental
import io.koraframework.resilient.circuitbreaker.exception.CallNotPermittedException
import org.awaitility.Awaitility.*
import org.awaitility.core.ConditionFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import io.koraframework.resilient.circuitbreaker.telemetry.impl.NoopCircuitBreakerTelemetry
import io.koraframework.resilient.circuitbreaker.telemetry.CircuitBreakerTelemetryConfig
import java.time.Duration
import java.util.concurrent.Callable
import java.util.function.Supplier

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@KspExperimental
class CircuitBreakerTests {

    private val WAIT_IN_OPEN: Duration = Duration.ofMillis(50)

    private fun awaitily(): ConditionFactory {
        return await().atMost(Duration.ofSeconds(1)).pollDelay(Duration.ofMillis(5))
    }

    @Test
    fun switchFromClosedToOpenToHalfOpenToOpenToHalfOpenToClosedForAccept() {
        // given
        val config = config(true, 50, WAIT_IN_OPEN, 2, 4L, 2L)
        val circuitBreaker = KoraCircuitBreaker("default", config, KoraCircuitBreakerPredicate(), NoopCircuitBreakerTelemetry.INSTANCE)

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

    private fun config(
        enabled: Boolean,
        failureRateThreshold: Int,
        waitDurationInOpenState: Duration,
        permittedCallsInHalfOpenState: Int,
        slidingWindowSize: Long,
        minimumRequiredCalls: Long,
    ): CircuitBreakerConfig {
        return object : CircuitBreakerConfig {
            override fun enabled() = enabled

            override fun failureRateThreshold() = failureRateThreshold

            override fun waitDurationInOpenState() = waitDurationInOpenState

            override fun permittedCallsInHalfOpenState() = permittedCallsInHalfOpenState

            override fun slidingWindowSize() = slidingWindowSize

            override fun minimumRequiredCalls() = minimumRequiredCalls

            override fun telemetry(): CircuitBreakerTelemetryConfig? = null
        }
    }
}
