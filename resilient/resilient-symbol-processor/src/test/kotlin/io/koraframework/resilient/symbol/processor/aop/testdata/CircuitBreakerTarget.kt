package io.koraframework.resilient.symbol.processor.aop.testdata

import io.koraframework.common.annotation.Component
import io.koraframework.common.annotation.Root
import io.koraframework.resilient.circuitbreaker.annotation.CircuitBreaker
import io.koraframework.resilient.circuitbreaker.annotation.CircuitBreakable

@CircuitBreaker("custom1")
interface TestCircuitBreaker : io.koraframework.resilient.circuitbreaker.CircuitBreaker

@Component
@Root
open class CircuitBreakerTarget {

    var alwaysFail = true

    @CircuitBreakable(TestCircuitBreaker::class)
    open fun getValueSync(): String {
        check(!alwaysFail) { "Failed" }
        return "OK"
    }

    @CircuitBreakable(TestCircuitBreaker::class)
    open fun getValueSyncVoid() {
        check(!alwaysFail) { "Failed" }
    }

    // Throws here is an alias for kotlin.jvm.Throws
    // Method should compile normally
    @Throws(IllegalStateException::class)
    @CircuitBreakable(TestCircuitBreaker::class)
    open fun throws(): String {
        check(!alwaysFail) { "Failed" }
        return "OK"
    }
}
