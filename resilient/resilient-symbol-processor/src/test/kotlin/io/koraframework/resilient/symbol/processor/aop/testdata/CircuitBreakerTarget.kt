package io.koraframework.resilient.symbol.processor.aop.testdata

import io.koraframework.common.Component
import io.koraframework.common.annotation.Root
import io.koraframework.resilient.circuitbreaker.annotation.CircuitBreaker

@Component
@Root
open class CircuitBreakerTarget {

    var alwaysFail = true

    @CircuitBreaker("custom1")
    open fun getValueSync(): String {
        check(!alwaysFail) { "Failed" }
        return "OK"
    }

    @CircuitBreaker("custom2")
    open fun getValueSyncVoid() {
        check(!alwaysFail) { "Failed" }
    }

    // Throws here is an alias for kotlin.jvm.Throws
    // Method should compile normally
    @Throws(IllegalStateException::class)
    @CircuitBreaker("customThrows")
    open fun throws(): String {
        check(!alwaysFail) { "Failed" }
        return "OK"
    }
}
